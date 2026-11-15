package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.RecruitmentBookmarkCursorDto;
import com.irummate.domain.recruitment.dto.RecruitmentBookmarkListResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentResponseDto;
import com.irummate.domain.recruitment.service.RecruitmentBookmarkService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
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

import java.util.List;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecruitmentBookmarkControllerTest {
    private final RecruitmentBookmarkService service = mock(RecruitmentBookmarkService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(2L, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new RecruitmentBookmarkController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionController()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addAndRemoveUseAuthenticatedUserAndReturnSuccess() throws Exception {
        mvc.perform(put("/api/recruitments/11/bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("즐겨찾기를 등록했습니다."));
        mvc.perform(delete("/api/recruitments/11/bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("즐겨찾기를 해제했습니다."));

        verify(service).addBookmark(2L, 11L);
        verify(service).removeBookmark(2L, 11L);
    }

    @Test
    void hiddenPostRegistrationReturnsNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND))
                .when(service).addBookmark(2L, 11L);

        mvc.perform(put("/api/recruitments/11/bookmark"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsItemsAndBookmarkCursorWithDefaultSize() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 11, 16, 10, 0, 0, 123456000);
        when(service.getMyBookmarks(2L, null, null, 10)).thenReturn(RecruitmentBookmarkListResponseDto.builder()
                .items(List.of(RecruitmentResponseDto.builder().recruitmentId(11L).title("모집글").bookmarked(true).build()))
                .hasNext(true).nextCursor(new RecruitmentBookmarkCursorDto(time, 80L)).build());

        mvc.perform(get("/api/recruitments/bookmarks/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.data.items[0].recruitmentId").value(11))
                .andExpect(jsonPath("$.data.items[0].bookmarked").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor.bookmarkedAt").value(time.toString()))
                .andExpect(jsonPath("$.data.nextCursor.id").value(80));
        verify(service).getMyBookmarks(2L, null, null, 10);
    }

    @Test
    void listForwardsCursorTimestampWithoutLosingPrecision() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 11, 16, 10, 0, 0, 123456000);
        when(service.getMyBookmarks(2L, time, 80L, 5)).thenReturn(
                new RecruitmentBookmarkListResponseDto(List.of(), false, null));

        mvc.perform(get("/api/recruitments/bookmarks/me")
                        .param("cursorBookmarkedAt", time.toString()).param("cursorId", "80").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        verify(service).getMyBookmarks(2L, time, 80L, 5);
    }

    @ParameterizedTest
    @CsvSource({"cursorBookmarkedAt,bad-date", "cursorId,abc", "size,abc", "cursorId,9223372036854775808"})
    void malformedListQueryReturnsBadRequest(String name, String value) throws Exception {
        mvc.perform(get("/api/recruitments/bookmarks/me").param(name, value))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidCursorFromServiceReturnsBadRequest() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT))
                .when(service).getMyBookmarks(2L, null, 80L, 10);

        mvc.perform(get("/api/recruitments/bookmarks/me").param("cursorId", "80"))
                .andExpect(status().isBadRequest());
    }
}
