package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.irummate.chatbot.FaqEmbeddingEvaluationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 같은 query 벡터로 A(DB의 Q+A), B(question-only)를 비교하는 수동 실험. DB 쓰기 없음. */
@EnabledIfEnvironmentVariable(named = "FAQ_SEARCH_AB_EVALUATION_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(readOnly = true)
class FaqEmbeddingAbEvaluationTest {
    private static final Path CACHE_DIRECTORY = Path.of(".gradle/faq-search-evaluation-cache");

    @Autowired
    private DormitoryFaqRepository repository;

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void comparesQuestionAndAnswerWithQuestionOnlyUsingIdenticalCachedQueries() throws IOException {
        Function<String, float[]> small = text -> FaqEmbeddingModelEvaluationTest.embed(openAIClient, text, EmbeddingModel.TEXT_EMBEDDING_3_SMALL);
        evaluate(repository, jdbc, small, small, false);
    }

    // 두 실험은 데이터, 지표, L2 계산, 보고서 및 DB 보존 검증을 공유한다.
    static void evaluate(DormitoryFaqRepository repository, JdbcTemplate jdbc,
                         Function<String, float[]> smallEmbed, Function<String, float[]> otherEmbed,
                         boolean compareModels) throws IOException {
        String otherModel = compareModels ? LARGE_MODEL : MODEL;
        String labelA = compareModels ? "small" : "A: Q+A";
        String labelB = compareModels ? "large" : "B: Question Only";
        var questions = FaqSearchEvaluationTest.loadQuestions();
        List<String> oosQuestions = FaqSearchEvaluationTest.loadOosQuestions();
        List<String> allQuestions = Stream.concat(questions.stream()
                .map(FaqSearchEvaluationTest.EvaluationQuestion::question), oosQuestions.stream()).toList();
        assertThat(allQuestions).hasSize(95).doesNotHaveDuplicates();

        List<DormitoryFaq> faqs = repository.findAll().stream()
                .sorted(Comparator.comparing(DormitoryFaq::getId)).toList();
        verifyDatabase(faqs, questions);
        String originalFingerprint = databaseFingerprint(jdbc);
        List<FaqVector> databaseVectors = faqs.stream()
                .map(faq -> new FaqVector(faq.getId(), faq.getQuestion(), faq.getEmbedding())).toList();
        List<FaqVector> aVectors = new ArrayList<>();
        EmbeddingCache queryCache = new EmbeddingCache(CACHE_DIRECTORY.resolve("queries"), MODEL);
        EmbeddingCache faqCache = new EmbeddingCache(CACHE_DIRECTORY.resolve(
                compareModels ? "faq-question-answer" : "faq-question-only"), otherModel);
        EmbeddingCache otherQueries = compareModels
                ? new EmbeddingCache(CACHE_DIRECTORY.resolve("queries"), otherModel) : queryCache;
        EmbeddingCache smallFaqCache = new EmbeddingCache(CACHE_DIRECTORY.resolve("faq-question-answer"), MODEL);
        Path reports = Path.of(compareModels ? "build/reports/faq-embedding-model-evaluation"
                : "build/reports/faq-search-ab-evaluation");
        Files.createDirectories(reports);

        try (PrintWriter log = writer(reports.resolve("evaluation.log"));
             PrintWriter inDomain = writer(reports.resolve("in-domain.tsv"));
             PrintWriter oos = writer(reports.resolve("oos.tsv"))) {
            log(log, "Run: %s%nModels: A=%s; B=%s; dimensions: %d; metric: L2%nCache: %s%n",
                    Instant.now(), MODEL, otherModel, DIMENSIONS, CACHE_DIRECTORY.toAbsolutePath());
            Map<String, float[]> queries = new LinkedHashMap<>();
            Map<String, float[]> bQueries = compareModels ? new LinkedHashMap<>() : queries;
            List<FaqVector> bVectors = new ArrayList<>();
            try {
                for (String question : allQuestions) {
                    queries.put(question, queryCache.get(question, smallEmbed));
                }
                // DB가 large로 전환되어도 과거 small baseline 캐시를 덮어쓰지 않는다.
                for (DormitoryFaq faq : faqs) {
                    aVectors.add(new FaqVector(faq.getId(), faq.getQuestion(), smallFaqCache.get(faqText(faq), smallEmbed)));
                }
                if (compareModels) {
                    for (String question : allQuestions) {
                        bQueries.put(question, otherQueries.get(question, otherEmbed));
                    }
                }
                for (DormitoryFaq faq : faqs) {
                    String text = compareModels ? faqText(faq) : faq.getQuestion();
                    bVectors.add(new FaqVector(faq.getId(), faq.getQuestion(),
                            faqCache.get(text, otherEmbed)));
                }
            } finally {
                log(log, "===== EMBEDDING CACHE =====%n%s%n%s%nSDK 내부 재시도를 제외한 embedding 서비스 호출 횟수입니다.%n",
                        queryCache.statistics(labelA + " query"), faqCache.statistics(labelB + " FAQ"));
                if (compareModels) {
                    log(log, "%s%nSmall FAQ baseline uses its model-specific cache, never the current large DB vectors.%n%s%n",
                            smallFaqCache.statistics("small FAQ Q+A"),
                            otherQueries.statistics("large query"));
                }
            }

            // 실제 pgvector <->와 Java L2를 in-domain 및 OOS query 하나씩 대조한다.
            if (sameVectors(aVectors, databaseVectors)) {
                verifyPgvector(repository, queries.get(allQuestions.getFirst()), aVectors, log);
                verifyPgvector(repository, queries.get(oosQuestions.getFirst()), aVectors, log);
            } else if (compareModels && sameVectors(bVectors, databaseVectors)) {
                verifyPgvector(repository, bQueries.get(allQuestions.getFirst()), bVectors, log);
                verifyPgvector(repository, bQueries.get(oosQuestions.getFirst()), bVectors, log);
            } else {
                log(log, "Historical small comparison uses cache; current DB vectors differ, so the pgvector model cross-check is skipped.%n");
            }

            Metrics a = new Metrics();
            Metrics b = new Metrics();
            List<String> improved = new ArrayList<>();
            List<String> regressed = new ArrayList<>();
            List<String> bothWrong = new ArrayList<>();
            inDomain.println("question\tstyle\texpectedFaqId\tA_top1FaqId\tA_top1Distance\tA_top3\tA_top1Correct\tA_top3Hit\tA_expectedFaqDistance\tB_top1FaqId\tB_top1Distance\tB_top3\tB_top1Correct\tB_top3Hit\tB_expectedFaqDistance");
            oos.println("question\tA_top1FaqId\tA_top1Question\tA_top1Distance\tB_top1FaqId\tB_top1Question\tB_top1Distance");

            for (var question : questions) {
                float[] query = queries.get(question.question());
                // 입력 원문은 같고, 모델 실험에서는 각 모델의 query와 FAQ 벡터만 서로 비교한다.
                Outcome resultA = a.inDomain(top3(query, aVectors), question.expectedFaqId());
                Outcome resultB = b.inDomain(top3(bQueries.get(question.question()), bVectors), question.expectedFaqId());
                log(log, "QUESTION: %s%nEXPECTED: %d%nA: %s%nB: %s%n--------------------------------%n",
                        question.question(), question.expectedFaqId(), resultA, resultB);
                inDomain.printf(Locale.ROOT, "%s\t%s\t%d\t%s\t%s%n", question.question(), question.style(),
                        question.expectedFaqId(), resultA.columns(), resultB.columns());
                inDomain.flush();
                String change = String.format(Locale.ROOT,
                        "%s | expected=%d | A=%d (%.9f), top3=%s | B=%d (%.9f), top3=%s",
                        question.question(), question.expectedFaqId(), resultA.first().id(), resultA.first().distance(),
                        resultA.ids(), resultB.first().id(), resultB.first().distance(), resultB.ids());
                if (!resultA.correct() && resultB.correct()) improved.add(change);
                if (resultA.correct() && !resultB.correct()) regressed.add(change);
                if (!resultA.correct() && !resultB.correct()) bothWrong.add(change);
            }

            for (String question : oosQuestions) {
                float[] query = queries.get(question);
                List<Match> resultA = top3(query, aVectors);
                List<Match> resultB = top3(bQueries.get(question), bVectors);
                a.oos.add(resultA.getFirst().distance());
                b.oos.add(resultB.getFirst().distance());
                log(log, "[OOS] QUESTION: %s%nA: %s%nB: %s%n--------------------------------%n", question, resultA, resultB);
                Match firstA = resultA.getFirst();
                Match firstB = resultB.getFirst();
                oos.printf(Locale.ROOT, "%s\t%d\t%s\t%s\t%d\t%s\t%s%n", question,
                        firstA.id(), firstA.question(), firstA.distance(), firstB.id(), firstB.question(), firstB.distance());
                oos.flush();
            }

            // 새 캐시 인스턴스로 디스크 재사용을 확인한다. 캐시 miss면 API 대신 즉시 실패한다.
            EmbeddingCache replayQueries = new EmbeddingCache(CACHE_DIRECTORY.resolve("queries"), MODEL);
            EmbeddingCache replayFaqs = new EmbeddingCache(CACHE_DIRECTORY.resolve(
                    compareModels ? "faq-question-answer" : "faq-question-only"), otherModel);
            EmbeddingCache replayOtherQueries = compareModels
                    ? new EmbeddingCache(CACHE_DIRECTORY.resolve("queries"), otherModel) : replayQueries;
            EmbeddingCache replaySmallFaqs = new EmbeddingCache(CACHE_DIRECTORY.resolve("faq-question-answer"), MODEL);
            Function<String, float[]> noApi = text -> { throw new AssertionError("Unexpected cache miss: " + text); };
            for (String question : allQuestions) {
                assertThat(replayQueries.get(question, noApi)).isEqualTo(queries.get(question));
                if (compareModels) {
                    assertThat(replayOtherQueries.get(question, noApi)).isEqualTo(bQueries.get(question));
                }
            }
            for (FaqVector faq : bVectors) {
                String text = compareModels ? faqText(faqs.stream().filter(item -> item.getId() == faq.id()).findFirst().orElseThrow())
                        : faq.question();
                assertThat(replayFaqs.get(text, noApi)).isEqualTo(faq.embedding());
            }
            if (compareModels) {
                for (int i = 0; i < faqs.size(); i++) {
                    assertThat(replaySmallFaqs.get(faqText(faqs.get(i)), noApi)).isEqualTo(aVectors.get(i).embedding());
                }
            }
            assertThat(databaseFingerprint(jdbc)).as("DB embedding 변경 금지").isEqualTo(originalFingerprint);

            String summary = String.format(Locale.ROOT, """
                    ===== %s =====
                    In-domain: %d; OOS: %d; model: %s; dimensions: %d; metric: L2
                    %s
                    %-28s %18s %18s
                    %-28s %17.2f%% %17.2f%%
                    %-28s %18d %18d
                    %-28s %17.2f%% %17.2f%%
                    %-28s %18d %18d
                    %-28s %18d %18d
                    %s
                    %s
                    ===== EMBEDDING CACHE =====
                    %s
                    %s
                    ===== DISK CACHE REPLAY (API DISABLED) =====
                    %s
                    %s
                    DB embedding fingerprint unchanged: %s
                    Expected FAQ distance is recorded only for Top-3 hits.
                    No threshold or OOS accept/reject decision is applied.
                    These are synthetic evaluation questions; existing baseline reports are preserved.
                    [IMPROVED] count=%d
                    %s
                    [REGRESSED] count=%d
                    %s
                    [BOTH WRONG] count=%d
                    %s
                    """, compareModels ? "EMBEDDING MODEL A/B" : "FAQ EMBEDDING A/B EVALUATION",
                    questions.size(), oosQuestions.size(), MODEL, DIMENSIONS,
                    compareModels ? "small/large both use exact FAQ Q+A text, model-specific queries, dimensions=1536 and the same Java L2 evaluator."
                            : "A and B use identical cached query vectors and the same Java L2 evaluator.",
                    "Metric", labelA, labelB,
                    "Top-1 Accuracy", a.accuracy(), b.accuracy(),
                    "Top-1 Correct", a.correct.size(), b.correct.size(),
                    "Recall@3", a.recall(), b.recall(),
                    "Top-3 Hit", a.expected.size(), b.expected.size(),
                    "Top-3 Miss", a.top1.size() - a.expected.size(), b.top1.size() - b.expected.size(),
                    a.statistics(labelA), b.statistics(labelB),
                    queryCache.statistics(labelA + " query"), faqCache.statistics(labelB + " FAQ"),
                    replayQueries.statistics(labelA + " query"), replayFaqs.statistics(labelB + " FAQ"),
                    originalFingerprint, improved.size(), lines(improved), regressed.size(), lines(regressed),
                    bothWrong.size(), lines(bothWrong));
            if (compareModels) {
                summary += String.format(Locale.ROOT, """
                        ===== MODEL CACHE DETAILS =====
                        small model: %s; large model: %s; dimensions: %d
                        %s
                        Small FAQ baseline uses its model-specific cache, never the current large DB vectors.
                        %s
                        %s
                        %s
                        Small total API calls: %d
                        Large total API calls: %d
                        SDK internal HTTP retries are not counted separately.
                        """, MODEL, otherModel, DIMENSIONS, smallFaqCache.statistics("small FAQ Q+A"),
                        otherQueries.statistics("large query"), replayOtherQueries.statistics("large query disk replay"),
                        replaySmallFaqs.statistics("small FAQ Q+A disk replay"),
                        queryCache.apiCalls() + smallFaqCache.apiCalls(), otherQueries.apiCalls() + faqCache.apiCalls());
            }
            log(log, "%s", summary);
            Files.writeString(reports.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
            assertThat(log.checkError() || inDomain.checkError() || oos.checkError()).isFalse();
        }
        System.out.println("A/B reports: " + reports.toAbsolutePath());
    }

    private static String faqText(DormitoryFaq faq) {
        return "질문: " + faq.getQuestion() + "\n답변: " + faq.getAnswer();
    }

    private static boolean sameVectors(List<FaqVector> first, List<FaqVector> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i).id() != second.get(i).id() || !Arrays.equals(first.get(i).embedding(), second.get(i).embedding())) return false;
        }
        return true;
    }

    private static void verifyDatabase(List<DormitoryFaq> faqs, List<FaqSearchEvaluationTest.EvaluationQuestion> questions) {
        assertThat(faqs).hasSize(25);
        Map<Long, DormitoryFaq> byId = faqs.stream().collect(Collectors.toMap(DormitoryFaq::getId, Function.identity()));
        for (var question : questions) {
            DormitoryFaq faq = byId.get(question.expectedFaqId());
            assertThat(faq).isNotNull();
            assertThat(faq.getQuestion()).isEqualTo(question.originalQuestion());
            assertThat(faq.isActive()).isTrue();
            validate(faq.getEmbedding());
        }
    }

    private static void verifyPgvector(DormitoryFaqRepository repository, float[] query, List<FaqVector> faqs, PrintWriter log) {
        var database = repository.findTop3ByEmbedding(Arrays.toString(query));
        List<Match> java = top3(query, faqs);
        assertThat(database).hasSize(3);
        double maxError = 0;
        for (int i = 0; i < java.size(); i++) {
            assertThat(java.get(i).id()).isEqualTo(database.get(i).getId());
            assertThat(java.get(i).distance()).isCloseTo(database.get(i).getDistance(), within(0.000001));
            maxError = Math.max(maxError, Math.abs(java.get(i).distance() - database.get(i).getDistance()));
        }
        log(log, "Java L2 vs pgvector <->: Top-3 IDs match, max distance error=%.12f (tolerance=0.000001)%n", maxError);
    }

    private static String databaseFingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT md5(string_agg(id::text || ':' || embedding::text, '|' ORDER BY id)) FROM dormitory_faq", String.class);
    }

    private record Outcome(List<Match> matches, boolean correct, Double expectedDistance) {
        Match first() { return matches.getFirst(); }
        boolean hit() { return expectedDistance != null; }
        List<Long> ids() { return matches.stream().map(Match::id).toList(); }
        String columns() {
            return String.format(Locale.ROOT, "%d\t%s\t%s\t%s\t%s\t%s", first().id(), first().distance(),
                    ids(), correct, hit(), expectedDistance);
        }
    }

    private static final class Metrics {
        private final List<Double> top1 = new ArrayList<>();
        private final List<Double> correct = new ArrayList<>();
        private final List<Double> expected = new ArrayList<>();
        private final List<Double> oos = new ArrayList<>();

        Outcome inDomain(List<Match> matches, long expectedId) {
            Double expectedDistance = matches.stream().filter(match -> match.id() == expectedId)
                    .map(Match::distance).findFirst().orElse(null);
            Outcome outcome = new Outcome(matches, matches.getFirst().id() == expectedId, expectedDistance);
            top1.add(outcome.first().distance());
            if (outcome.correct()) correct.add(outcome.first().distance());
            if (outcome.hit()) expected.add(expectedDistance);
            return outcome;
        }

        double accuracy() { return 100.0 * correct.size() / top1.size(); }
        double recall() { return 100.0 * expected.size() / top1.size(); }
        String statistics(String label) {
            return label + " distance statistics (count / average / min / max / median)\n"
                    + stats("In-domain Top-1", top1) + "\n" + stats("Correct Top-1", correct) + "\n"
                    + stats("Expected FAQ (Top-3 hits)", expected) + "\n" + stats("OOS Top-1", oos);
        }
    }

    private static String stats(String label, List<Double> values) {
        if (values.isEmpty()) return label + ": 0 / N/A / N/A / N/A / N/A";
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
        return String.format(Locale.ROOT, "%s: %d / %.9f / %.9f / %.9f / %.9f", label, values.size(),
                values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), sorted.getFirst(), sorted.getLast(), median);
    }

    private static String lines(List<String> values) { return values.isEmpty() ? "None" : String.join("\n", values); }
    private static PrintWriter writer(Path path) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }
    private static void log(PrintWriter writer, String format, Object... arguments) {
        System.out.printf(Locale.ROOT, format, arguments);
        writer.printf(Locale.ROOT, format, arguments);
        writer.flush();
    }
}
