package com.irummate.notice;

import com.irummate.domain.notice.controller.NoticeController;
import com.irummate.domain.notice.dto.NoticesResponseDto;
import com.irummate.domain.notice.service.NoticeService;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.config.SecurityConfig;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtAuthenticationFilter;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {NoticeController.class, NoticeApiTest.ProtectedController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, NoticeApiTest.ProtectedController.class})
class NoticeApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean NoticeService service;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean HashIdsUtils hashIds;
    @MockitoBean UsersRepository users;

    @RestController
    static class ProtectedController {
        @GetMapping("/api/notice-test/protected") String protectedApi() { return "ok"; }
    }

    @Test void anonymousUserCanReadEmptyPage() throws Exception {
        when(service.findNotices(0, 10, null)).thenReturn(new NoticesResponseDto(List.of(), 0, 10, 0, 0));
        mvc.perform(get("/api/notices")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test void passesPaginationAndKeywordToService() throws Exception {
        when(service.findNotices(2, 5, "생활관")).thenReturn(new NoticesResponseDto(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/notices").param("page", "2").param("size", "5").param("keyword", "생활관"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2));
        verify(service).findNotices(2, 5, "생활관");
    }

    @Test void invalidPaginationReturns400() throws Exception {
        when(service.findNotices(-1, 10, null)).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        mvc.perform(get("/api/notices").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test void invalidParameterTypeReturns400() throws Exception {
        mvc.perform(get("/api/notices").param("page", "oops")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/notices").param("size", "1.5")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void databaseFailureDoesNotReturnSuccess() throws Exception {
        when(service.findNotices(0, 10, null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test"));
        mvc.perform(get("/api/notices")).andExpect(status().isServiceUnavailable());
    }

    @Test void existingJwtChainStillRequiresAndAcceptsUserAuthentication() throws Exception {
        mvc.perform(get("/api/notice-test/protected")).andExpect(status().isUnauthorized());
        Claims claims = mock(Claims.class);
        when(jwt.validateAccessToken("user-token")).thenReturn(true);
        when(jwt.parseClaims("user-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("sid", String.class)).thenReturn("notice-test-session");
        when(hashIds.decode("user-id")).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(Users.builder().oauthId("test").nickname("test").build()));
        mvc.perform(get("/api/notice-test/protected").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
        verify(users).findById(7L);
    }
}
