package com.irummate.domain.admin.controller;

import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationPageResponseDto;
import com.irummate.domain.admin.dto.AdminCertificationResponseDto;
import com.irummate.domain.admin.service.AdminCertificationService;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.exception.GlobalExceptionController;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.irummate.domain.certification.entity.CertificationStatus.PENDING;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminCertificationControllerTest {
    private final AdminCertificationService service = mock(AdminCertificationService.class);
    private final UUID publicId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(2L, null, List.of()));
        mvc = mockMvc(service);
    }

    private MockMvc mockMvc(AdminCertificationService adminService) {
        return MockMvcBuilders.standaloneSetup(new AdminCertificationController(adminService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionController())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "detail, not-a-uuid", "approve, not-a-uuid", "reject, not-a-uuid",
            "detail, 1-1-1-1-1", "approve, 1-1-1-1-1", "reject, 1-1-1-1-1"
    })
    void malformedUuidReturnsCommonBadRequestBeforeService(String operation, String id) throws Exception {
        String path = "/api/admin/certifications/" + id;
        MockHttpServletRequestBuilder request = "detail".equals(operation)
                ? get(path)
                : patch(path + "/" + operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3,\"adminComment\":\"인증 사진을 확인해주세요.\"}");

        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("요청 값이나 형식이 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    @Test
    void listUsesDefaultPageAndSizeAndReturnsPageObject() throws Exception {
        when(service.getCertifications(null, null, 0, 15))
                .thenReturn(new AdminCertificationPageResponseDto(List.of(response()), 0, 15, 100, 7, true));

        mvc.perform(get("/api/admin/certifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.certifications[0].certificationId").value(publicId.toString()))
                .andExpect(jsonPath("$.data.certifications[0].nickname").value("사용자"))
                .andExpect(jsonPath("$.data.certifications[0].imageUrl").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.totalElements").value(100))
                .andExpect(jsonPath("$.data.totalPages").value(7))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.size").doesNotExist());
    }

    @Test
    void listBindsFiltersAndReturnsAnEmptyArrayWhenNoRecordsMatch() throws Exception {
        when(service.getCertifications(PENDING, "2026-2", 2, 1))
                .thenReturn(new AdminCertificationPageResponseDto(List.of(), 2, 1, 0, 0, false));

        mvc.perform(get("/api/admin/certifications")
                        .param("status", "PENDING").param("semester", "2026-2")
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certifications").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "page,NaN", "size,1.5", "status,UNKNOWN", "semester,2026-3"})
    void invalidListParametersReturnCommonBadRequestBeforeQuery(String name, String value) throws Exception {
        CertificationRepository repository = mock(CertificationRepository.class);
        var adminService = new AdminCertificationService(
                repository, mock(HashIdsUtils.class), mock(S3Utils.class), mock(UsersRepository.class));

        mockMvc(adminService).perform(get("/api/admin/certifications").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(repository);
    }

    @Test
    void detailBindsUuidAndSerializesItAsString() throws Exception {
        when(service.getCertification(publicId)).thenReturn(new AdminCertificationResponseDto(
                publicId, 4L, "existing-user-id", "실명", "사용자", "2026-2",
                "https://example.com/image?signature=test", "PENDING", null, null, null, null));

        mvc.perform(get("/api/admin/certifications/{certificationId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificationId").value(publicId.toString()))
                .andExpect(jsonPath("$.data.realName").value("실명"))
                .andExpect(jsonPath("$.data.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/image?signature=test"));
        verify(service).getCertification(publicId);
    }

    @Test
    void approvalReceivesUuidAndInternalAdminId() throws Exception {
        when(service.approveCertification(eq(2L), eq(publicId), any())).thenReturn(response());

        mvc.perform(patch("/api/admin/certifications/{certificationId}/approve", publicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificationId").value(publicId.toString()));
        verify(service).approveCertification(eq(2L), eq(publicId),
                argThat(request -> request.getExpectedVersion().equals(3L)));
    }

    @Test
    void rejectionReceivesUuidAndReason() throws Exception {
        when(service.rejectCertification(eq(2L), eq(publicId), any())).thenReturn(response());

        mvc.perform(patch("/api/admin/certifications/{certificationId}/reject", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3,\"adminComment\":\"인증 사진을 확인해주세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificationId").value(publicId.toString()));
        verify(service).rejectCertification(eq(2L), eq(publicId),
                argThat((AdminCertificationRejectRequestDto request) ->
                        "인증 사진을 확인해주세요.".equals(request.getAdminComment())
                                && request.getExpectedVersion().equals(3L)));
    }

    @Test
    void missingCertificationKeepsNotFoundResponse() throws Exception {
        when(service.getCertification(publicId))
                .thenThrow(new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));

        mvc.perform(get("/api/admin/certifications/{certificationId}", publicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.CERTIFICATION_NOT_FOUND.getMessage()));
    }

    @Test
    void approvalOutsideCurrentSemesterReturnsCommonConflictResponse() throws Exception {
        when(service.approveCertification(eq(2L), eq(publicId), any()))
                .thenThrow(new BusinessException(ErrorCode.CERTIFICATION_SEMESTER_MISMATCH));

        mvc.perform(patch("/api/admin/certifications/{certificationId}/approve", publicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_SEMESTER_MISMATCH"))
                .andExpect(jsonPath("$.message").value("현재 학기의 인증 요청만 승인할 수 있습니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":-1}"})
    void approvalRequiresNonNegativeReviewVersion(String body) throws Exception {
        mvc.perform(patch("/api/admin/certifications/{certificationId}/approve", publicId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectionRequiresReviewVersion() throws Exception {
        mvc.perform(patch("/api/admin/certifications/{certificationId}/reject", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminComment\":\"다시 제출해주세요.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 501})
    void rejectionValidatesReasonBeforeCallingService(int length) throws Exception {
        String reason = length <= 1 ? " ".repeat(length) : "가".repeat(length);

        mvc.perform(patch("/api/admin/certifications/{certificationId}/reject", publicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":3,"adminComment":"%s"}
                                """.formatted(reason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("adminComment"));
        verifyNoInteractions(service);
    }

    @Test
    void optimisticConflictReturnsCommonConflictResponse() throws Exception {
        when(service.approveCertification(eq(2L), eq(publicId), any()))
                .thenThrow(new OptimisticLockingFailureException("internal optimistic lock details"));

        mvc.perform(patch("/api/admin/certifications/{certificationId}/approve", publicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPDATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UPDATE_CONFLICT.getMessage()));
    }

    private AdminCertificationResponseDto response() {
        return new AdminCertificationResponseDto(
                publicId, 4L, "existing-user-id", null, "사용자", "2026-2",
                null, "PENDING", null, null, null, null);
    }
}
