package com.irummate.report;

import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.domain.report.controller.ReportController;
import com.irummate.domain.report.entity.Report;
import com.irummate.domain.report.entity.ReportSourceType;
import com.irummate.domain.report.repository.ReportRepository;
import com.irummate.domain.report.service.ReportService;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.aop.ValidationAspect;
import com.irummate.global.config.SecurityConfig;
import com.irummate.global.security.SecurityErrorResponseWriter;
import com.irummate.global.jwt.JwtAuthenticationFilter;
import com.irummate.global.jwt.JwtProperties;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 기존 JWT 필터·인가 AOP·실제 ReportService를 거쳐 요청과 저장 데이터를 검증한다.
@WebMvcTest(controllers = ReportController.class, properties = {
        "jwt.secret=report-test-secret-key-with-at-least-32-bytes",
        "jwt.access-token-expiration=3600000", "jwt.refresh-token-expiration=1209600000",
        "hashids.salt=report-test-salt", "hashids.minlength=8"
})
@Import({SecurityConfig.class, SecurityErrorResponseWriter.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, JwtProperties.class,
        HashIdsUtils.class, ValidationAspect.class, ReportService.class, ReportApiTest.AopConfig.class})
class ReportApiTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AopConfig { }

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired HashIdsUtils hashIds;
    @Autowired JsonMapper json;
    @MockitoBean UsersRepository users;
    @MockitoBean ReportRepository reports;
    @MockitoBean UserDetailsRepository details;
    @MockitoBean UserPreferencesRepository preferences;
    @MockitoBean MatchingConfigRepository matchingConfig;
    @MockitoBean CertificationRepository certifications;

    private Users reporter;
    private Users reported;
    private String userToken;
    private String adminToken;

    @BeforeEach void setup() {
        reporter = account(1L, UserRole.USER);
        reported = account(2L, UserRole.USER);
        var admin = account(3L, UserRole.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(reporter));
        when(users.findById(2L)).thenReturn(Optional.of(reported));
        when(users.findById(3L)).thenReturn(Optional.of(admin));
        userToken = token(1L, UserRole.USER);
        adminToken = token(3L, UserRole.ADMIN);
    }

    private Users account(Long id, UserRole role) {
        var user = mock(Users.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }

    private String token(Long id, UserRole role) {
        return "Bearer " + jwt.createAccessToken(hashIds.encode(id), role.name(),
                "a0000000-0000-0000-0000-000000000001");
    }

    private String validBody() {
        return "{\"sourceType\":\"CHAT\",\"reason\":\"  반복적인 욕설  \"}";
    }

    @Test void createsReportUsingJwtReporterAndDecodedTarget() throws Exception {
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(2L))
                        .header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("CREATED"));
        var saved = ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReporter()).isSameAs(reporter);
        assertThat(saved.getValue().getReported()).isSameAs(reported);
        assertThat(saved.getValue().getSourceType()).isEqualTo(ReportSourceType.CHAT);
        assertThat(saved.getValue().getReason()).isEqualTo("반복적인 욕설");
    }

    @Test void rejectsSelfAndMissingTargetWithoutSaving() throws Exception {
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(1L))
                        .header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(99L))
                        .header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"sourceType\":\"CHAT\",\"reason\":\"   \"}",
            "{\"sourceType\":\"UNKNOWN\",\"reason\":\"사유\"}",
            "{\"sourceType\":0,\"reason\":\"사유\"}", "{"
    })
    void rejectsInvalidBody(String body) throws Exception {
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(2L))
                        .header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test void rejectsOversizedReason() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("sourceType", "PROFILE", "reason", "가".repeat(2001)));
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(2L))
                        .header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test void requiresAuthenticationOnAllEndpoints() throws Exception {
        mvc.perform(post("/api/users/{id}/reports", hashIds.encode(2L))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports/{id}", hashIds.encode(10L))).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }

    @Test void deniesOrdinaryUserAccessToAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/reports").header("Authorization", userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/{id}", hashIds.encode(10L)).header("Authorization", userToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    private ReportRepository.ReportView reportView() {
        var report = mock(ReportRepository.ReportView.class);
        when(report.getReportId()).thenReturn(10L);
        when(report.getReporterId()).thenReturn(1L);
        when(report.getReporterNickname()).thenReturn("신고자");
        when(report.getReportedId()).thenReturn(2L);
        when(report.getReportedNickname()).thenReturn("대상");
        when(report.getSourceType()).thenReturn(ReportSourceType.CHAT);
        when(report.getReason()).thenReturn("신고 사유");
        when(report.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 15, 12, 0));
        return report;
    }

    @Test void adminListAndDetailEncodeAllIds() throws Exception {
        var report = reportView();
        when(reports.findReports(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));
        when(reports.findReport(10L)).thenReturn(Optional.of(report));
        mvc.perform(get("/api/admin/reports").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reportId").value(hashIds.encode(10L)))
                .andExpect(jsonPath("$.data.content[0].reporterId").value(hashIds.encode(1L)))
                .andExpect(jsonPath("$.data.content[0].reportedId").value(hashIds.encode(2L)));
        mvc.perform(get("/api/admin/reports/{id}", hashIds.encode(10L)).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reason").value("신고 사유"))
                .andExpect(jsonPath("$.data.reportId").value(hashIds.encode(10L)));
    }

    @Test void adminCanReadEmptyListAndMissingReportReturns404() throws Exception {
        when(reports.findReports(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get("/api/admin/reports").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/admin/reports/{id}", hashIds.encode(99L)).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test void rejectsBadHashidsAndPages() throws Exception {
        mvc.perform(post("/api/users/!/reports").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isBadRequest());
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=oops", "?page=2147483647&size=20")) {
            mvc.perform(get("/api/admin/reports" + query).header("Authorization", adminToken))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reports);
    }
}
