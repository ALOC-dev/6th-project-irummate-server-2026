package com.irummate.chatbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.irummate.chatbot.FaqEmbeddingEvaluationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaqEmbeddingEvaluationSupportTest {
    @TempDir
    Path directory;

    @Test
    void requests1536DimensionsForBothModelsWithoutCallingTheApi() {
        for (var model : List.of(com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
                com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_3_LARGE)) {
            var parameters = FaqEmbeddingModelEvaluationTest.parameters("같은 원문", model);
            assertThat(parameters.model()).isEqualTo(model);
            assertThat(parameters.dimensions()).contains(1536L);
        }
    }

    @Test
    void seedsSmallFaqCacheFromDatabaseWithoutAnApiCall() throws IOException {
        EmbeddingCache cache = new EmbeddingCache(directory, MODEL);
        float[] vector = new float[DIMENSIONS];
        vector[DIMENSIONS - 1] = 0.25f;
        assertThat(cache.seedFromDatabase("질문: 질문\n답변: 답변", vector)).isTrue();
        assertThat(cache.seedFromDatabase("질문: 질문\n답변: 답변", vector)).isFalse();
        assertThat(cache.get("질문: 질문\n답변: 답변", text -> {
            throw new AssertionError("Must reuse DB vector");
        })).isEqualTo(vector);
        assertThat(cache.apiCalls()).isZero();
        assertThat(cache.hits()).isEqualTo(1);
        EmbeddingCache large = new EmbeddingCache(directory, LARGE_MODEL);
        large.get("질문: 질문\n답변: 답변", text -> new float[DIMENSIONS]);
        assertThat(large.apiCalls()).isEqualTo(1);
    }

    @Test
    void calculatesL2OverAll1536DimensionsAndSortsByDistance() {
        float[] query = new float[DIMENSIONS];
        float[] near = new float[DIMENSIONS];
        near[0] = 3;
        near[DIMENSIONS - 1] = 4;
        float[] far = new float[DIMENSIONS];
        far[DIMENSIONS - 1] = 6;

        assertThat(l2(query, near)).isEqualTo(5);
        assertThat(l2(near, query)).isEqualTo(5);
        assertThat(l2(query, query)).isZero();
        assertThat(top3(query, List.of(new FaqVector(4, "far", far), new FaqVector(3, "near", near),
                new FaqVector(2, "tie", near), new FaqVector(1, "exact", query))))
                .extracting(Match::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void reusesPersistedCacheForTheSameExactQuestionAndModel() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingCache cache = new EmbeddingCache(directory, MODEL);
        float[] expected = new float[DIMENSIONS];
        expected[0] = -0.25f;
        expected[DIMENSIONS - 1] = 0.75f;
        assertThat(cache.get("질문", text -> { calls.incrementAndGet(); return expected; })).isEqualTo(expected);

        EmbeddingCache reopened = new EmbeddingCache(directory, MODEL);
        assertThat(reopened.get("질문", text -> { throw new AssertionError("API must not be called"); })).isEqualTo(expected);
        assertThat(calls).hasValue(1);
        assertThat(cache.apiCalls()).isEqualTo(1);
        assertThat(reopened.apiCalls()).isZero();
        assertThat(reopened.hits()).isEqualTo(1);
    }

    @Test
    void changedQuestionOrModelCreatesOnlyTheMissingEmbedding() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        var embed = (java.util.function.Function<String, float[]>) text -> {
            calls.incrementAndGet();
            return new float[DIMENSIONS];
        };
        EmbeddingCache cache = new EmbeddingCache(directory, MODEL);
        cache.get("질문", embed);
        cache.get("질문 ", embed);
        cache.get("질문", embed);
        new EmbeddingCache(directory, "another-model").get("질문", embed);

        assertThat(calls).hasValue(3);
        assertThat(cache.hits()).isEqualTo(1);
    }

    @Test
    void replacesTruncatedOrWrongMetadataCache() throws IOException {
        EmbeddingCache cache = new EmbeddingCache(directory, MODEL);
        cache.get("질문", text -> new float[DIMENSIONS]);
        Path file;
        try (var files = Files.list(directory)) {
            file = files.findFirst().orElseThrow();
        }
        Files.write(file, new byte[]{1});
        cache.get("질문", text -> new float[DIMENSIONS]);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeUTF("faq-embedding-cache-v1");
            output.writeUTF(MODEL);
            output.writeUTF("다른 질문");
            output.writeInt(DIMENSIONS);
        }
        cache.get("질문", text -> new float[DIMENSIONS]);

        assertThat(cache.apiCalls()).isEqualTo(3);
        assertThat(cache.statistics("test")).contains("invalid entries=2");
    }

    @Test
    void rejectsInvalidVectorsBeforePersistingOrCalculatingDistance() throws IOException {
        EmbeddingCache cache = new EmbeddingCache(directory, MODEL);
        assertThatThrownBy(() -> cache.get("짧은 벡터", text -> new float[9]))
                .isInstanceOf(IllegalArgumentException.class);
        float[] invalid = new float[DIMENSIONS];
        invalid[DIMENSIONS - 1] = Float.NaN;
        assertThatThrownBy(() -> cache.get("NaN", text -> invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l2(new float[9], new float[DIMENSIONS])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l2(new float[DIMENSIONS], invalid)).isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }
}
