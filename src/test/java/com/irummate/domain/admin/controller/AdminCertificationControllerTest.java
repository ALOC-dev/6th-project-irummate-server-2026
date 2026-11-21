package com.irummate.domain.admin.controller;

import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationResponseDto;
import com.irummate.domain.admin.service.AdminCertificationService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.exception.GlobalExceptionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
        mvc = MockMvcBuilders.standaloneSetup(new AdminCertificationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionController())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "approve", "reject"})
    void malformedUuidReturnsCommonBadRequestBeforeService(String operation) throws Exception {
        String path = "/api/admin/certifications/not-a-uuid";
        MockHttpServletRequestBuilder request = "detail".equals(operation)
                ? get(path)
                : patch(path + "/" + operation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminComment\":\"인증 사진을 확인해주세요.\"}");

        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("요청 값이나 형식이 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    @Test
    void detailBindsUuidAndSerializesItAsString() throws Exception {
        when(service.getCertification(publicId)).thenReturn(response());

        mvc.perform(get("/api/admin/certifications/{certificationId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificationId").value(publicId.toString()));
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
                .andExpect(status().isBadRequest());
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
