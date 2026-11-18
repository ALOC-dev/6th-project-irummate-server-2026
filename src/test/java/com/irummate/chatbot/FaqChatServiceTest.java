package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqAnswerService;
import com.irummate.domain.chatbot.service.FaqChatService;
import com.irummate.domain.chatbot.service.FaqSearchService;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.openai.errors.OpenAIException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaqChatServiceTest {
    private static final String QUESTION = "기숙사비 할부 가능해?";
    private final FaqSearchService search = mock(FaqSearchService.class);
    private final FaqSelectionService selector = mock(FaqSelectionService.class);
    private final FaqAnswerService generator = mock(FaqAnswerService.class);
    private final FaqChatService chat = new FaqChatService(search, selector, generator);
    private final FaqSearchResult selected = mock(FaqSearchResult.class);
    private final List<FaqSearchResult> candidates = List.of(mock(FaqSearchResult.class), selected, mock(FaqSearchResult.class));

    @Test
    void searchesAndSelectsOnceThenGeneratesWithSelectedFaqOnly() {
        when(search.search(QUESTION)).thenReturn(candidates);
        when(selector.select(QUESTION, candidates)).thenReturn(Optional.of(selected));
        when(generator.generateAnswer(QUESTION, selected)).thenReturn("분납이 가능합니다.");

        assertThat(chat.chat(QUESTION)).isEqualTo("분납이 가능합니다.");
        var order = inOrder(search, selector, generator);
        order.verify(search).search(QUESTION);
        order.verify(selector).select(QUESTION, candidates);
        order.verify(generator).generateAnswer(QUESTION, selected);
        verifyNoMoreInteractions(search, selector, generator);
    }

    @Test
    void noneSkipsGeneration() {
        when(search.search(QUESTION)).thenReturn(candidates);
        when(selector.select(QUESTION, candidates)).thenReturn(Optional.empty());
        assertThat(chat.chat(QUESTION)).isEqualTo(FaqChatService.FALLBACK_MESSAGE);
        verifyNoInteractions(generator);
    }

    @Test
    void noCandidatesSkipsSelectionAndGeneration() {
        when(search.search(QUESTION)).thenReturn(List.of());
        assertThat(chat.chat(QUESTION)).isEqualTo(FaqChatService.FALLBACK_MESSAGE);
        verifyNoInteractions(selector, generator);
    }

    @Test
    void invalidQuestionsFailBeforeSearch() {
        for (String question : Arrays.asList(null, "", " \t\n", "가".repeat(501))) {
            assertThatThrownBy(() -> chat.chat(question)).isInstanceOfSatisfying(BusinessException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        verifyNoInteractions(search, selector, generator);
    }

    @Test
    void propagatesFailureFromEachStage() {
        var searchFailure = new org.springframework.dao.DataAccessResourceFailureException("DB unavailable");
        when(search.search(QUESTION)).thenThrow(searchFailure);
        assertThatThrownBy(() -> chat.chat(QUESTION)).isSameAs(searchFailure);
        verifyNoInteractions(selector, generator);

        doReturn(candidates).when(search).search(QUESTION);
        var selectionFailure = new IllegalStateException("Selector unavailable");
        when(selector.select(QUESTION, candidates)).thenThrow(selectionFailure);
        assertThatThrownBy(() -> chat.chat(QUESTION)).isSameAs(selectionFailure);
        verifyNoInteractions(generator);

        doReturn(Optional.of(selected)).when(selector).select(QUESTION, candidates);
        var answerFailure = new IllegalStateException("Generation unavailable");
        when(generator.generateAnswer(QUESTION, selected)).thenThrow(answerFailure);
        assertThatThrownBy(() -> chat.chat(QUESTION)).isSameAs(answerFailure);
    }

    @Test
    void surfacesUpstreamSdkFailureWithSafeMessage() {
        var failure = new OpenAIException("provider payload");
        when(search.search(QUESTION)).thenThrow(failure);
        assertThatThrownBy(() -> chat.chat(QUESTION)).isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI FAQ search or selection failed").hasCause(failure);
        verifyNoInteractions(selector, generator);
    }
}
