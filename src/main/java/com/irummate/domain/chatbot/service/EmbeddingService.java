package com.irummate.domain.chatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final OpenAIClient openAIClient;

    public List<Float> embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .input(text)
                .model(EmbeddingModel.TEXT_EMBEDDING_3_LARGE)
                .dimensions(1536L)
                .build();

        CreateEmbeddingResponse response =
                openAIClient.embeddings().create(params);

        List<Float> vector = response.data().get(0).embedding();
        if (vector.size() != 1536 || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalStateException("OpenAI returned an invalid 1536-dimensional embedding");
        }
        return vector;
    }

    public float[] embedToArray(String text) {
        List<Float> embedding = embed(text);

        float[] result = new float[embedding.size()];

        for(int i=0; i<embedding.size(); i++)
            result[i] = embedding.get(i);

        return result;
    }
}
