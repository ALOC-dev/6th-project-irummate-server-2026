package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static com.irummate.chatbot.FaqEmbeddingEvaluationSupport.validate;

/** 모델만 변경하는 수동 비교. 기존 small 캐시/DB를 재사용하며 운영 embedding은 변경하지 않는다. */
@EnabledIfEnvironmentVariable(named = "FAQ_EMBEDDING_MODEL_EVALUATION_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(readOnly = true)
class FaqEmbeddingModelEvaluationTest {
    @Autowired
    private DormitoryFaqRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OpenAIClient openAIClient;

    @Test
    void comparesSmallAndLargeAt1536Dimensions() throws IOException {
        FaqEmbeddingAbEvaluationTest.evaluate(repository, jdbc,
                text -> embed(openAIClient, text, EmbeddingModel.TEXT_EMBEDDING_3_SMALL),
                text -> embed(openAIClient, text, EmbeddingModel.TEXT_EMBEDDING_3_LARGE), true);
    }

    static EmbeddingCreateParams parameters(String text, EmbeddingModel model) {
        return EmbeddingCreateParams.builder().input(text).model(model).dimensions(1536L).build();
    }

    static float[] embed(OpenAIClient client, String text, EmbeddingModel model) {
        List<Float> values = client.embeddings().create(parameters(text, model)).data().getFirst().embedding();
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = values.get(i);
        }
        validate(vector);
        return vector;
    }
}
