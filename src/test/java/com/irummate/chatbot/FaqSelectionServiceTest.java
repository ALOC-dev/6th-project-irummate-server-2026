package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import com.irummate.domain.chatbot.service.FaqSelectionService.FaqSelection;
import com.irummate.domain.chatbot.service.FaqSelectionService.FaqSelectionOutput;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class FaqSelectionServiceTest {
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final ResponseService responses = mock(ResponseService.class);
    private final StructuredResponse<FaqSelectionOutput> response = mock(StructuredResponse.class);
    private final StructuredResponseOutputMessage.Content<FaqSelectionOutput> content = mock(StructuredResponseOutputMessage.Content.class);
    private final FaqSelectionService selector = new FaqSelectionService(client, "configured-selector-model");
    private final List<FaqSearchResult> candidates = List.of(candidate(11), candidate(9), candidate(3));

    @BeforeEach
    void setUpResponse() {
        when(client.responses()).thenReturn(responses);
        doReturn(response).when(responses).create(any(StructuredResponseCreateParams.class));
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        StructuredResponseOutputItem<FaqSelectionOutput> item = mock(StructuredResponseOutputItem.class);
        StructuredResponseOutputMessage<FaqSelectionOutput> message = mock(StructuredResponseOutputMessage.class);
        when(response.output()).thenReturn(List.of(item));
        when(item.message()).thenReturn(Optional.of(message));
        when(message.content()).thenReturn(List.of(content));
    }

    @ParameterizedTest
    @EnumSource(FaqSelection.class)
    void mapsOnlyProvidedCandidatePositionsOrNone(FaqSelection selection) {
        selectResponse(selection);
        var result = selector.select("기숙사 와이파이 돼?", candidates);
        if (selection == FaqSelection.NONE) assertThat(result).isEmpty();
        else assertThat(result).containsSame(candidates.get(selection.ordinal() - 1));
    }

    @Test
    void acceptsOneOrTwoCandidatesAndRejectsMissingPosition() {
        selectResponse(FaqSelection.CANDIDATE_1);
        assertThat(selector.select("질문", candidates.subList(0, 1))).containsSame(candidates.getFirst());
        selectResponse(FaqSelection.CANDIDATE_2);
        assertThat(selector.select("질문", candidates.subList(0, 2))).containsSame(candidates.get(1));
        selectResponse(FaqSelection.CANDIDATE_3);
        assertThatThrownBy(() -> selector.select("질문", candidates.subList(0, 2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not provided");
    }

    @Test
    void emptyCandidatesDoNotCallApi() {
        assertThat(selector.select("질문", List.of())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void propagatesApiFailureInsteadOfReturningNone() {
        RuntimeException failure = new IllegalStateException("API unavailable");
        doThrow(failure).when(responses).create(any(StructuredResponseCreateParams.class));
        assertThatThrownBy(() -> selector.select("질문", candidates)).isSameAs(failure);
    }

    @Test
    void rejectsIncompleteRefusalAndMissingParsedOutput() {
        when(response.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        assertThatThrownBy(() -> selector.select("질문", candidates)).isInstanceOf(IllegalStateException.class);
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        when(content.refusal()).thenReturn(Optional.of(mock(ResponseOutputRefusal.class)));
        assertThatThrownBy(() -> selector.select("질문", candidates)).isInstanceOf(IllegalStateException.class);
        when(content.refusal()).thenReturn(Optional.empty());
        when(content.outputText()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> selector.select("질문", candidates)).isInstanceOf(IllegalStateException.class);
        selectResponse(null);
        assertThatThrownBy(() -> selector.select("질문", candidates)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usesConfiguredModelAndQuestionAnswerWithoutDistanceOrDatabaseId() {
        selectResponse(FaqSelection.NONE);
        selector.select("질문 원문", candidates);
        ArgumentCaptor<StructuredResponseCreateParams> captor = ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        verify(responses).create(captor.capture());
        var raw = captor.getValue().rawParams();
        assertThat(raw.model().orElseThrow().asString()).isEqualTo("configured-selector-model");
        assertThat(raw.input().orElseThrow().asText()).contains("질문 원문", "CANDIDATE_1", "question:", "answer:")
                .doesNotContain("777.123", "distance", "id=");
        assertThat(captor.getValue().responseType()).isEqualTo(FaqSelectionOutput.class);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> selector.select(" ", candidates)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selector.select("질문", List.of(candidates.getFirst(), candidates.getFirst(), candidates.getFirst(), candidates.getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void selectResponse(FaqSelection selection) {
        FaqSelectionOutput parsed = new FaqSelectionOutput();
        parsed.selection = selection;
        when(content.outputText()).thenReturn(Optional.of(parsed));
    }

    private FaqSearchResult candidate(long id) {
        FaqSearchResult faq = mock(FaqSearchResult.class);
        when(faq.getId()).thenReturn(id);
        when(faq.getQuestion()).thenReturn("FAQ 질문 " + id);
        when(faq.getAnswer()).thenReturn("FAQ 답변 " + id);
        when(faq.getDistance()).thenReturn(777.123);
        return faq;
    }
}
