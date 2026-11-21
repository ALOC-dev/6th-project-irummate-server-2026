package com.irummate.chatbot;

import com.irummate.domain.chatbot.service.FaqSearchService;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FAQ_SELECTOR_LIVE_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(FaqSelectionService.class)
@Transactional(readOnly = true)
class FaqSelectorLiveTest {
    @Autowired FaqSearchService search;
    @Autowired FaqSelectionService selector;

    @Test
    void selectsInternetFaqAndRejectsTuitionQuestionThroughActualPgvectorSearch() {
        String wifi = "기숙사 와이파이 돼?";
        var candidates = search.search(wifi);
        assertThat(candidates.getFirst().getId()).isEqualTo(9L);
        var result = selector.select(wifi, candidates);
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getId()).isEqualTo(9L);
        String tuition = "서울시립대 이번 학기 등록금이 얼마야?";
        assertThat(selector.select(tuition, search.search(tuition))).isEmpty();
        System.out.println("Live: large embedding -> pgvector -> selector: wifi=FAQ 9, tuition=NONE");
    }
}
