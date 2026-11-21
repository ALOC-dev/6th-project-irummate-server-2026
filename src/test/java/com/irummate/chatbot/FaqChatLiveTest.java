package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqAnswerService;
import com.irummate.domain.chatbot.service.FaqChatService;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfEnvironmentVariable(named = "FAQ_CHAT_LIVE_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({FaqChatService.class, FaqSelectionService.class, FaqAnswerService.class})
@Transactional(readOnly = true)
class FaqChatLiveTest {
    @Autowired private FaqChatService chat;
    @MockitoSpyBean private FaqAnswerService generator;

    @ParameterizedTest(name = "{0}")
    @MethodSource("questions")
    void answersFromSelectedFaq(Question test) {
        assertAnswerFromFaq(test);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("promptRegressionQuestions")
    void preservesRequiredDetailsAndOmitsUnrelatedOnes(Question test) {
        String answer = assertAnswerFromFaq(test);
        if (test.id() == 24) {
            assertThat(answer).doesNotContain("만취", "응급실", "보호자", "술에 취");
        }
    }

    private String assertAnswerFromFaq(Question test) {
        String answer = chat.chat(test.text());
        var faqCaptor = ArgumentCaptor.forClass(FaqSearchResult.class);
        verify(generator).generateAnswer(eq(test.text()), faqCaptor.capture());
        var faq = faqCaptor.getValue();
        System.out.printf("QUESTION: %s%nFAQ %d: %s%nSOURCE:%n%s%nANSWER:%n%s%n%n",
                test.text(), faq.getId(), faq.getQuestion(), faq.getAnswer(), answer);

        assertThat(faq.getId()).isEqualTo(test.id());
        assertThat(answer).isNotBlank().isNotEqualTo(FaqChatService.FALLBACK_MESSAGE);
        test.requiredPatterns().forEach(pattern -> assertThat(answer).containsPattern(pattern));
        return answer;
    }

    @Test
    void rejectsTuitionWithoutCallingAnswerGenerator() {
        String question = "서울시립대 이번 학기 등록금 얼마야?";
        String answer = chat.chat(question);
        System.out.printf("QUESTION: %s%nFAQ: NONE%nANSWER:%n%s%n%n", question, answer);
        assertThat(answer).isEqualTo(FaqChatService.FALLBACK_MESSAGE);
        verifyNoInteractions(generator);
    }

    private static Stream<Question> promptRegressionQuestions() {
        return Stream.of(
                new Question("기숙사 통금 있어?", 24, List.of("통금|통행금지", "없", "소음", "벌점")),
                new Question("입실할 때 필요한 서류가 뭐야?", 14,
                        List.of("공지\\s*문|입실\\s*공지", "신분증", "한국인", "외국인", "입실일\\s*기준",
                                "6\\s*개월", "결핵", "홍역", "2\\s*회"))
        );
    }

    private static Stream<Question> questions() {
        return Stream.of(
                new Question("기숙사비 할부 가능해?", 1, List.of("정규학기[^\\n]{0,35}4\\s*회", "방학[^\\n]{0,35}2\\s*회")),
                new Question("전기장판 가져가도 돼?", 23, List.of("전기장판", "금지|안 됩|불가|수 없|안 돼")),
                new Question("기숙사 와이파이 돼?", 9, List.of("Wi-Fi|와이파이")),
                new Question("밤 12시에 공용 세탁기를 돌려도 이용 가능한 시간인가요?", 5, List.of("6\\s*시", "1\\s*시")),
                new Question("집에 쓰던 소형 냉장고가 있는데 크기나 연식 제한과 등록 절차가 있나요?", 6,
                        List.of("50\\s*[Ll]|50리터", "10\\s*년", "등록")),
                new Question("생활관으로 물건을 배송받으려면 주소와 우편번호를 어떻게 쓰나요?", 8,
                        List.of("서울시립대로\\s*163", "02504", "호")),
                new Question("기숙사에 선발됐지만 입주를 취소하려면 어떻게 신청하나요?", 13,
                        List.of("재학생", "신입생", "퇴실환불신청서", "02-6490-5186")),
                new Question("정규학기와 방학 기숙사비 환불 기준을 모두 알려줘.", 2,
                        List.of("정규학기", "방학", "95\\s*%", "75\\s*%", "62\\.5[0]?\\s*%", "50\\s*%", "37\\.5[0]?\\s*%", "25\\s*%", "0\\s*%", "76\\s*일", "31\\s*일")),
                new Question("친구 기숙사에 데려와도 돼?", 11, List.of("외부인|기숙사생", "금지|안 됩|불가|수 없|안 돼"))
        );
    }

    private record Question(String text, long id, List<String> requiredPatterns) {
        @Override public String toString() {
            return text;
        }
    }
}
