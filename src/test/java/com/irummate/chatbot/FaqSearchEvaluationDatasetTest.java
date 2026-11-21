package com.irummate.chatbot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FaqSearchEvaluationDatasetTest {

    @Test
    void containsThreeDistinctParaphrasesPerFaqAndTwentyOosQuestions() throws IOException {
        var questions = FaqSearchEvaluationTest.loadQuestions();
        List<String> oos = FaqSearchEvaluationTest.loadOosQuestions();

        assertThat(questions).hasSize(75);
        var byFaq = questions.stream().collect(Collectors.groupingBy(
                FaqSearchEvaluationTest.EvaluationQuestion::expectedFaqId));
        assertThat(byFaq.keySet()).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 25).boxed().toList());
        byFaq.values().forEach(group -> {
            assertThat(group).hasSize(3);
            assertThat(group).extracting(FaqSearchEvaluationTest.EvaluationQuestion::originalQuestion)
                    .containsOnly(group.getFirst().originalQuestion());
            assertThat(group).extracting(FaqSearchEvaluationTest.EvaluationQuestion::style)
                    .containsExactlyInAnyOrder("paraphrase", "colloquial", "different");
            group.forEach(question -> assertThat(question.question()).isNotBlank().isNotEqualTo(question.originalQuestion()));
        });
        assertThat(oos).hasSize(20);
        assertThat(Stream.concat(questions.stream().map(FaqSearchEvaluationTest.EvaluationQuestion::question), oos.stream()).toList())
                .doesNotHaveDuplicates().allSatisfy(question -> assertThat(question).isNotBlank());
    }
}
