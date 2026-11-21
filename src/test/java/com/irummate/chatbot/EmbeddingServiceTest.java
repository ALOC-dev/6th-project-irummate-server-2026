package com.irummate.chatbot;

import com.irummate.domain.chatbot.service.EmbeddingService;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmbeddingServiceTest {
    @Test
    void requestsLarge1536AndKeepsListAndArrayPublicApis() {
        OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        CreateEmbeddingResponse response = mock(CreateEmbeddingResponse.class);
        Embedding embedding = mock(Embedding.class);
        when(client.embeddings().create(any(EmbeddingCreateParams.class))).thenReturn(response);
        when(response.data()).thenReturn(List.of(embedding));
        when(embedding.embedding()).thenReturn(Collections.nCopies(1536, 0.25f));
        EmbeddingService service = new EmbeddingService(client);
        assertThat(service.embedToArray("질문")).hasSize(1536).containsOnly(0.25f);
        ArgumentCaptor<EmbeddingCreateParams> captor = ArgumentCaptor.forClass(EmbeddingCreateParams.class);
        verify(client.embeddings()).create(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo(EmbeddingModel.TEXT_EMBEDDING_3_LARGE);
        assertThat(captor.getValue().dimensions()).contains(1536L);
        when(embedding.embedding()).thenReturn(List.of(0.25f));
        assertThatThrownBy(() -> service.embed("질문")).isInstanceOf(IllegalStateException.class);
    }
}
