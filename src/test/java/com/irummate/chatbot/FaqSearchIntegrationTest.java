package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.entity.FaqCategory;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.EmbeddingService;
import com.irummate.domain.chatbot.service.FaqSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = FaqSearchIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class FaqSearchIntegrationTest {

    // 앱 전체를 스캔하지 않아 Seeder 및 실제 OpenAI 호출을 실행하지 않는다.
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DormitoryFaq.class)
    @EnableJpaRepositories(basePackageClasses = DormitoryFaqRepository.class)
    @Import(FaqSearchService.class)
    static class TestConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                    .withInitScript("db/test-init.sql");
        }
    }

    @Autowired
    private FaqSearchService searchService;

    @Autowired
    private DormitoryFaqRepository repository;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Test
    void returnsTopThreeByL2DistanceAndExcludesInactiveAndMissingEmbeddings() {
        String question = "기숙사에 친구 데려와도 돼?";
        when(embeddingService.embedToArray(question)).thenReturn(vector(1, 0));

        DormitoryFaq nearest = faq("외부 친구나 방문객이 기숙사에 들어와도 되나요?", vector(1, 1), true);
        DormitoryFaq second = faq("두 번째 FAQ", vector(3, 0), true);
        DormitoryFaq third = faq("세 번째 FAQ", vector(-2, 4), true);
        repository.saveAllAndFlush(List.of(
                faq("Top-3 밖의 FAQ", vector(7, 0), true), third, second, nearest,
                faq("비활성 FAQ", vector(1, 0), false), faq("임베딩 없는 FAQ", null, true)));

        List<FaqSearchResult> results = searchService.search(question);

        assertThat(results).extracting(FaqSearchResult::getId)
                .containsExactly(nearest.getId(), second.getId(), third.getId());
        assertThat(results).extracting(FaqSearchResult::getDistance).containsExactly(1.0, 2.0, 5.0);
        assertThat(results.getFirst().getQuestion()).isEqualTo(nearest.getQuestion());
        assertThat(results.getFirst().getAnswer()).isEqualTo(nearest.getAnswer());
        // 두 번째 벡터의 cosine 거리가 더 가깝지만 L2에서는 첫 번째가 먼저 나와야 한다.
        verify(embeddingService).embedToArray(question);
    }

    @Test
    void returnsAvailableResultsWhenFewerThanThreeMatch() {
        when(embeddingService.embedToArray("질문")).thenReturn(vector(1, 0));
        DormitoryFaq faq = repository.saveAndFlush(faq("한 개의 FAQ", vector(1, 0), true));

        assertThat(searchService.search("질문")).extracting(FaqSearchResult::getId)
                .containsExactly(faq.getId());
    }

    @Test
    void returnsEmptyWhenNoSearchableFaqExists() {
        when(embeddingService.embedToArray("질문")).thenReturn(vector(1, 0));
        repository.saveAllAndFlush(List.of(
                faq("비활성 FAQ", vector(1, 0), false), faq("임베딩 없는 FAQ", null, true)));

        assertThat(searchService.search("질문")).isEmpty();
    }

    private DormitoryFaq faq(String question, float[] embedding, boolean active) {
        return DormitoryFaq.builder()
                .category(FaqCategory.FACILITY)
                .question(question)
                .answer("테스트 답변: " + question)
                .isActive(active)
                .embedding(embedding)
                .build();
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1536];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
