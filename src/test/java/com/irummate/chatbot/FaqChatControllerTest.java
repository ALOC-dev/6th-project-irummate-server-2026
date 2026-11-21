package com.irummate.chatbot;

import com.irummate.domain.chatbot.controller.FaqChatController;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqAnswerService;
import com.irummate.domain.chatbot.service.FaqChatService;
import com.irummate.domain.chatbot.service.FaqSearchService;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.config.SecurityConfig;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtAuthenticationFilter;
import com.irummate.global.jwt.JwtProperties;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import com.openai.errors.OpenAIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FaqChatController.class, properties = {
        "spring.config.import=",
        "jwt.secret=faq-chat-test-signing-key-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "hashids.salt=faq-chat-test-salt",
        "hashids.minlength=8"
})
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, HashIdsUtils.class, FaqChatService.class})
class FaqChatControllerTest {
    private static final String PATH = "/api/chatbot/ask";
    private static final String QUESTION = "기숙사 통금 있어?";
    private static final String VALID_BODY = """
            {"question":"기숙사 통금 있어?"}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private HashIdsUtils hashIds;
    @MockitoBean private UsersRepository users;
    @MockitoSpyBean private FaqChatService chat;
    @MockitoBean private FaqSearchService search;
    @MockitoBean private FaqSelectionService selector;
    @MockitoBean private FaqAnswerService answerService;

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void authenticatedRequestRunsOneSearchSelectionAndAnswer(UserRole role) throws Exception {
        var selected = mock(FaqSearchResult.class);
        List<FaqSearchResult> candidates = List.of(mock(FaqSearchResult.class), selected, mock(FaqSearchResult.class));
        when(search.search(QUESTION)).thenReturn(candidates);
        when(selector.select(QUESTION, candidates)).thenReturn(Optional.of(selected));
        when(answerService.generateAnswer(QUESTION, selected)).thenReturn("야간 통행금지 시간은 없습니다.");

        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(role))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.answer").value("야간 통행금지 시간은 없습니다."));

        verify(chat).chat(QUESTION);
        var order = inOrder(search, selector, answerService);
        order.verify(search).search(QUESTION);
        order.verify(selector).select(QUESTION, candidates);
        order.verify(answerService).generateAnswer(QUESTION, selected);
        verifyNoMoreInteractions(search, selector, answerService);
    }

    @Test
    void rejectsAnonymousAndInvalidTokenBeforeChat() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chat, search, selector, answerService);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidInputBeforeChat(String body) throws Exception {
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(chat, search, selector, answerService);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"question\":null}", "{\"question\":\"\"}", "{\"question\":\"   \"}",
                "{\"question\":\"" + "가".repeat(501) + "\"}", "{\"question\":", "");
    }

    @Test
    void noneReturnsNormalResponseWithoutGeneration() throws Exception {
        String question = "서울시립대 이번 학기 등록금 얼마야?";
        var candidates = List.of(mock(FaqSearchResult.class));
        when(search.search(question)).thenReturn(candidates);
        when(selector.select(question, candidates)).thenReturn(Optional.empty());
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(FaqChatService.FALLBACK_MESSAGE));
        verify(search).search(question);
        verify(selector).select(question, candidates);
        verifyNoInteractions(answerService);
        verifyNoMoreInteractions(search, selector);
    }

    @Test
    void preservesSharedBusinessErrorResponse() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT)).when(chat).chat(QUESTION);
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()));
        verifyNoInteractions(search, selector, answerService);
    }

    @Test
    void openAiFailureReturnsGenericServerError() throws Exception {
        when(search.search(QUESTION)).thenThrow(new OpenAIException("internal provider payload"));
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(selector, answerService);
    }

    private String bearer(UserRole role) {
        long userId = 42L;
        Users user = mock(Users.class);
        when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        return "Bearer " + tokens.createAccessToken(hashIds.encode(userId), role.name(), "faq-test-session");
    }

    @Test
    void acceptsExactly500CharactersWithoutExternalCalls() throws Exception {
        String question = "가".repeat(500);
        when(search.search(question)).thenReturn(List.of());
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(FaqChatService.FALLBACK_MESSAGE));
        verify(search).search(question);
        verifyNoInteractions(selector, answerService);
    }

    @Test
    void databaseAndMalformedModelFailuresKeepTheirHttpContracts() throws Exception {
        String token = bearer(UserRole.USER);
        when(search.search(QUESTION)).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("test database unavailable"));
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(selector, answerService);

        FaqSearchResult selected = mock(FaqSearchResult.class);
        var candidates = List.of(selected);
        doReturn(candidates).when(search).search(QUESTION);
        when(selector.select(QUESTION, candidates)).thenReturn(Optional.of(selected));
        when(answerService.generateAnswer(QUESTION, selected)).thenThrow(
                new IllegalStateException("FAQ answer response did not contain a valid answer"));
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
