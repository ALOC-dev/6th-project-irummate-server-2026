package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.RecruitmentListResponseDto;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.service.RecruitmentService;
import com.irummate.global.exception.GlobalExceptionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecruitmentCursorControllerTest {
    private final RecruitmentService service = mock(RecruitmentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new RecruitmentController(service))
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
            "cursorCreatedAt,not-a-date",
            "cursorId,not-a-number",
            "cursorId,9223372036854775808",
            "size,not-a-number",
            "size,2147483648",
            "category,NOT_A_CATEGORY"
    })
    void malformedListQueryReturnsBadRequestBeforeService(String parameter, String value) throws Exception {
        mvc.perform(get("/api/recruitments").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void firstRequestReturnsCursorEnvelopeAndEmptyItems() throws Exception {
        when(service.getRecruitments(1L, null, null, null, 10)).thenReturn(emptyResponse());

        mvc.perform(get("/api/recruitments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        verify(service).getRecruitments(1L, null, null, null, 10);
    }

    @Test
    void cursorTimestampBindsWithoutLosingFractionalSeconds() throws Exception {
        LocalDateTime time = LocalDateTime.parse("2026-11-15T12:00:00.123456");
        when(service.getRecruitments(1L, RecruitmentCategory.MEAL, time, 103L, 20)).thenReturn(emptyResponse());

        mvc.perform(get("/api/recruitments")
                        .param("category", "MEAL")
                        .param("cursorCreatedAt", "2026-11-15T12:00:00.123456")
                        .param("cursorId", "103")
                        .param("size", "20"))
                .andExpect(status().isOk());
        verify(service).getRecruitments(1L, RecruitmentCategory.MEAL, time, 103L, 20);
    }

    private RecruitmentListResponseDto emptyResponse() {
        return RecruitmentListResponseDto.builder().items(List.of()).hasNext(false).nextCursor(null).build();
    }
}
