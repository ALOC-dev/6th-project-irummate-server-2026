package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수동 평가 전용. 75개 in-domain + 20개 OOS 질문으로 실제 embedding API를 사용한다.
 * 기존 live 테스트의 최소 설정을 재사용해 Seeder를 로드하지 않고 기존 DB만 읽는다.
 * 운영 TODO: 서버 시작 시 Seeder 실행을 one-time ingestion으로 분리한다.
 */
@EnabledIfEnvironmentVariable(named = "FAQ_SEARCH_EVALUATION_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(readOnly = true)
class FaqSearchEvaluationTest {

    @Autowired
    private FaqSearchService searchService;

    @Autowired
    private DormitoryFaqRepository repository;

    @Test
    void evaluatesInDomainAndOosQuestions() throws IOException {
        List<EvaluationQuestion> questions = loadQuestions();
        List<String> oosQuestions = loadOosQuestions();
        // 유료 API를 호출하기 전에 현재 DB의 1~25번 ID와 원문, 검색 가능 여부를 검증한다.
        verifyDatabase(questions);

        Path reportDirectory = Path.of("build/reports/faq-search-evaluation");
        Files.createDirectories(reportDirectory);
        int top1Correct = 0;
        int top3Hits = 0;
        List<Double> top1Distances = new ArrayList<>();
        List<Double> correctDistances = new ArrayList<>();
        List<Double> expectedDistances = new ArrayList<>();
        List<Double> oosDistances = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();

        try (PrintWriter log = writer(reportDirectory.resolve("evaluation.log"));
             PrintWriter inDomain = writer(reportDirectory.resolve("in-domain.tsv"));
             PrintWriter oos = writer(reportDirectory.resolve("oos.tsv"))) {
            log(log, "Run: %s%nModel: text-embedding-3-large; dimensions: 1536; metric: L2 (<->)%n", Instant.now());
            inDomain.println("question\tstyle\texpectedFaqId\ttop1FaqId\ttop3FaqIds\ttop1Distance\texpectedFaqDistance\ttop1Correct\ttop3ContainsExpected");
            oos.println("question\ttop1FaqId\ttop1Question\ttop1Distance");

            for (EvaluationQuestion question : questions) {
                List<FaqSearchResult> results = searchService.search(question.question());
                verifyResults(results);
                FaqSearchResult first = results.getFirst();
                boolean correct = first.getId().equals(question.expectedFaqId());
                Double expectedDistance = results.stream()
                        .filter(result -> result.getId().equals(question.expectedFaqId()))
                        .map(FaqSearchResult::getDistance).findFirst().orElse(null);
                boolean hit = expectedDistance != null;
                if (correct) {
                    top1Correct++;
                    correctDistances.add(first.getDistance());
                }
                if (hit) {
                    top3Hits++;
                    expectedDistances.add(expectedDistance);
                }
                top1Distances.add(first.getDistance());
                List<Long> top3Ids = results.stream().map(FaqSearchResult::getId).toList();
                log(log, "QUESTION: %s%nSTYLE: %s%nEXPECTED: %d (%s)%n",
                        question.question(), question.style(), question.expectedFaqId(), question.originalQuestion());
                for (int i = 0; i < results.size(); i++) {
                    FaqSearchResult result = results.get(i);
                    log(log, "TOP %d: id=%d; question=%s; distance=%.9f%n",
                            i + 1, result.getId(), result.getQuestion(), result.getDistance());
                }
                log(log, "EXPECTED_FAQ_DISTANCE=%s%nTOP1_CORRECT=%s%nTOP3_HIT=%s%n--------------------------------%n",
                        expectedDistance, correct, hit);
                inDomain.printf(Locale.ROOT, "%s\t%s\t%d\t%d\t%s\t%s\t%s\t%s\t%s%n",
                        question.question(), question.style(), question.expectedFaqId(), first.getId(),
                        top3Ids, first.getDistance(), expectedDistance, correct, hit);
                inDomain.flush();
                if (!correct) {
                    mismatches.add(String.format(Locale.ROOT,
                            "%s | expected=%d (%s), top1=%d (%s), distance=%.9f, top3=%s, hit=%s",
                            question.question(), question.expectedFaqId(), question.originalQuestion(),
                            first.getId(), first.getQuestion(), first.getDistance(), top3Ids, hit));
                }
            }

            for (String question : oosQuestions) {
                List<FaqSearchResult> results = searchService.search(question);
                verifyResults(results);
                FaqSearchResult first = results.getFirst();
                oosDistances.add(first.getDistance());
                log(log, "[OOS]%nQUESTION: %s%nTOP1: id=%d; question=%s; distance=%.9f%n--------------------------------%n",
                        question, first.getId(), first.getQuestion(), first.getDistance());
                oos.printf(Locale.ROOT, "%s\t%d\t%s\t%s%n",
                        question, first.getId(), first.getQuestion(), first.getDistance());
                oos.flush();
            }

            String summary = String.format(Locale.ROOT, """
                    ===== FAQ SEARCH EVALUATION =====
                    Total questions: %d
                    Top-1 correct: %d
                    Top-1 accuracy: %.2f%%
                    Top-3 hit: %d
                    Recall@3: %.2f%%
                    OOS questions: %d
                    %s
                    %s
                    %s
                    %s
                    Expected FAQ distance is available only when the expected FAQ is in Top-3.
                    These are retrieval metrics on a synthetic dataset, not a production threshold.
                    ===== TOP-1 MISMATCHES =====
                    %s
                    """, questions.size(), top1Correct, 100.0 * top1Correct / questions.size(),
                    top3Hits, 100.0 * top3Hits / questions.size(), oosQuestions.size(),
                    statistics("In-domain all Top-1 distance", top1Distances),
                    statistics("In-domain correct Top-1 distance", correctDistances),
                    statistics("Expected FAQ distance (Top-3 hits)", expectedDistances),
                    statistics("OOS Top-1 distance", oosDistances),
                    mismatches.isEmpty() ? "None" : String.join("\n", mismatches));
            log(log, "%s", summary);
            Files.writeString(reportDirectory.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
            assertThat(log.checkError() || inDomain.checkError() || oos.checkError())
                    .as("평가 결과 파일 저장 실패").isFalse();
        }
        System.out.println("Evaluation reports: " + reportDirectory.toAbsolutePath());
    }

    private void verifyDatabase(List<EvaluationQuestion> questions) {
        Map<Long, DormitoryFaq> faqs = repository.findAll().stream()
                .collect(Collectors.toMap(DormitoryFaq::getId, Function.identity()));
        assertThat(faqs).hasSize(25);
        for (EvaluationQuestion question : questions) {
            DormitoryFaq faq = faqs.get(question.expectedFaqId());
            assertThat(faq).as("FAQ ID %d", question.expectedFaqId()).isNotNull();
            assertThat(faq.getQuestion()).isEqualTo(question.originalQuestion());
            assertThat(faq.isActive()).isTrue();
            assertThat(faq.getEmbedding()).hasSize(1536);
        }
    }

    private void verifyResults(List<FaqSearchResult> results) {
        assertThat(results).hasSize(3);
        assertThat(results).extracting(FaqSearchResult::getDistance).isSorted();
        for (FaqSearchResult result : results) {
            assertThat(result.getDistance()).isNotNull().isFinite().isGreaterThanOrEqualTo(0.0);
        }
    }

    static List<EvaluationQuestion> loadQuestions() throws IOException {
        List<String> lines = readLines("/chatbot/faq-search-evaluation.tsv");
        List<String> styles = List.of("paraphrase", "colloquial", "different");
        List<EvaluationQuestion> questions = new ArrayList<>();
        assertThat(lines).hasSize(26);
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split("\t", -1);
            assertThat(columns).hasSize(5);
            for (int i = 0; i < styles.size(); i++) {
                questions.add(new EvaluationQuestion(columns[i + 2], Long.valueOf(columns[0]), columns[1], styles.get(i)));
            }
        }
        return List.copyOf(questions);
    }

    static List<String> loadOosQuestions() throws IOException {
        return readLines("/chatbot/faq-search-oos.txt");
    }

    private static List<String> readLines(String resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(FaqSearchEvaluationTest.class.getResourceAsStream(resource)), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        }
    }

    private static String statistics(String label, List<Double> distances) {
        if (distances.isEmpty()) {
            return label + ": count=0, mean=N/A, min=N/A, max=N/A, median=N/A";
        }
        List<Double> sorted = distances.stream().sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
        return String.format(Locale.ROOT, "%s: count=%d, mean=%.9f, min=%.9f, max=%.9f, median=%.9f",
                label, sorted.size(), sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                sorted.getFirst(), sorted.getLast(), median);
    }

    private static PrintWriter writer(Path path) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    private static void log(PrintWriter writer, String format, Object... arguments) {
        System.out.printf(Locale.ROOT, format, arguments);
        writer.printf(Locale.ROOT, format, arguments);
        writer.flush();
    }

    record EvaluationQuestion(String question, Long expectedFaqId, String originalQuestion, String style) {
    }
}
