package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqAnswerService;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "FAQ_ANSWER_ACCURACY_LIVE_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@Import(FaqAnswerService.class)
@Transactional(readOnly = true)
class FaqAnswerAccuracyLiveTest {
    @Autowired private DormitoryFaqRepository repository;
    @Autowired private FaqAnswerService answers;

    // Pin each original selection so the test measures generation independently of retrieval.
    @ParameterizedTest(name = "EDGE-{0}")
    @CsvSource({
            "030, 16", "031, 14", "032, 13", "055, 22", "073, 12",
            "034, 2", "048, 6", "056, 14", "060, 23", "065, 8", "067, 24"
    })
    void preservesRelevantFactsFromOriginalSelection(String number, long faqId) throws Exception {
        String id = "EDGE-" + number;
        String only = System.getenv("FAQ_ANSWER_ACCURACY_CASES");
        assumeTrue(only == null || Arrays.asList(only.split(",")).contains(number), "Outside the selected cases");
        String question;
        try (var cases = getClass().getResourceAsStream("/chatbot/faq-edge-cases.json")) {
            var testCase = JsonMapper.builder().build().readTree(cases).valueStream()
                    .filter(node -> node.get("id").asText().equals(id)).findFirst().orElseThrow();
            question = testCase.get("question").asText();
        }
        DormitoryFaq faq = repository.findById(faqId).orElseThrow();
        assertThat(faq.isActive()).isTrue();
        String answer = answers.generateAnswer(question, new SelectedFaq(faq));
        System.out.printf("EDGE_CASE %s FAQ %d%nQUESTION:%n%s%nSOURCE:%n%s%nANSWER:%n%s%nEND_EDGE_CASE%n",
                id, faqId, question, faq.getAnswer(), answer);

        assertThat(answer).isNotBlank();
        switch (number) {
            case "030" -> assertThat(answer).contains("추가", "취소", "퇴실")
                    .containsPattern("공지|공고").containsPattern("여석|빈자리|빈 방")
                    .containsPattern("확인할 수 없|확인되지 않|나와 있지 않|명시되어 있지 않|안내되어 있지 않|알 수 없|안내에는 없");
            case "031" -> assertThat(answer).contains("신분증", "결핵", "외국인", "홍역")
                    .containsPattern("공지\\s*문|입실\\s*공지").containsPattern("입실일\\s*기준").containsPattern("6\\s*개월")
                    .containsPattern("2\\s*회").containsPattern("확인할 수 없|확인되지 않|명시되어 있지 않|안내되어 있지 않|나와 있지 않|나오지 않")
                    .doesNotContain("보건소에서", "병원에서")
                    .doesNotContainPattern("6\\s*개월\\s*이내(?:의|에)?\\s*(?:발급(?:된)?\\s*)?홍역");
            case "032" -> assertThat(answer).contains("재학생", "신입생", "Q&A", "비밀글", "퇴실환불신청서", "02-6490-5186")
                    .containsPattern("확인할 수 없|확인되지 않|명시되어 있지 않|안내되어 있지 않|나와 있지 않|나오지 않");
            case "055" -> assertThat(answer).containsPattern("입실일\\s*기준").containsPattern("6\\s*개월")
                    .containsPattern("기준에 맞지 않|충족하지 않|제출할 수 없|유효하지 않|인정되지 않|제출 기준을 충족하지 못|제출할 수는 없")
                    .doesNotContain("보건소", "발급", "5일");
            case "073" -> assertThat(answer).contains("퇴실").containsPattern("거주\\s*자격을\\s*(?:상실하면|상실한\\s*경우|잃으면|잃은\\s*경우)")
                    .doesNotContain("무조건", "무관하게");
            case "034" -> assertThat(answer).contains("정규학기", "방학").containsPattern("62\\.5[0]?\\s*%").containsPattern("50\\s*%");
            case "048" -> assertThat(answer).contains("등록").containsPattern("50\\s*[Ll]|50리터").containsPattern("10\\s*년");
            case "056" -> assertThat(answer).contains("공지", "신분증", "결핵", "홍역").containsPattern("6\\s*개월").containsPattern("2\\s*회");
            case "060" -> assertThat(answer).containsPattern("헤어\\s*드라이").containsPattern("예외|챙겨|가져가").doesNotContain("공지");
            case "065" -> assertThat(answer).contains("02504", "서울시립대로 163", "0000호").doesNotContain("공지");
            case "067" -> assertThat(answer).contains("없", "소음", "벌점").doesNotContain("만취", "응급실", "보호자", "공지");
            default -> throw new AssertionError("Unexpected evaluation case " + id);
        }
    }

    private record SelectedFaq(DormitoryFaq faq) implements FaqSearchResult {
        public Long getId() { return faq.getId(); }
        public String getQuestion() { return faq.getQuestion(); }
        public String getAnswer() { return faq.getAnswer(); }
        public Double getDistance() { return null; }
    }
}
