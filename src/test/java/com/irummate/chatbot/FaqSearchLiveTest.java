package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.EmbeddingService;
import com.irummate.domain.chatbot.service.FaqSearchService;
import com.irummate.global.config.OpenAiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수동 검색 확인용: FAQ_SEARCH_LIVE_TEST=true일 때만 .env의 DB와 OpenAI를 사용한다.
 * Seeder를 로드하지 않으며, 스키마 변경이나 FAQ 적재 없이 검색 결과만 읽는다.
 */
@EnabledIfEnvironmentVariable(named = "FAQ_SEARCH_LIVE_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(readOnly = true)
class FaqSearchLiveTest {

    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DormitoryFaq.class)
    @EnableJpaRepositories(basePackageClasses = DormitoryFaqRepository.class)
    @Import({OpenAiConfig.class, EmbeddingService.class, FaqSearchService.class})
    static class TestConfig {
    }

    @Autowired
    private FaqSearchService searchService;

    @Autowired
    private DormitoryFaqRepository repository;

    @Test
    void findsVisitorFaqFirst() {
        assertThat(repository.count()).as("기존 DB에 FAQ가 적재되어 있어야 합니다").isGreaterThanOrEqualTo(3);

        List<FaqSearchResult> results = searchService.search("기숙사에 친구 데려와도 돼?");

        for (FaqSearchResult result : results) {
            System.out.printf("id = %d%nquestion = %s%ndistance = %s%n%n",
                    result.getId(), result.getQuestion(), result.getDistance());
        }
        assertThat(results).hasSize(3);
        assertThat(results).extracting(FaqSearchResult::getDistance).isSorted();
        assertThat(results.getFirst().getQuestion())
                .isEqualTo("외부 친구나 방문객이 기숙사에 들어와도 되나요?");
    }
}
