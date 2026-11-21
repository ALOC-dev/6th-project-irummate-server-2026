package com.irummate.chatbot;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.FaqSelectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.irummate.chatbot.FaqEmbeddingEvaluationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FAQ_SELECTOR_EVALUATION_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(FaqSelectionService.class)
@Transactional(readOnly = true)
class FaqSelectorEvaluationTest {
    @Autowired DormitoryFaqRepository repository;
    @Autowired FaqSelectionService selector;
    @Value("${openai.faq-selector.model:gpt-6-luna}") String model;

    @Test
    void evaluatesSelectorUsingCachedLargeQueriesAndExistingFaqData() throws IOException {
        var questions = FaqSearchEvaluationTest.loadQuestions();
        var oosQuestions = FaqSearchEvaluationTest.loadOosQuestions();
        Map<Long, DormitoryFaq> faqs = repository.findAll().stream()
                .collect(Collectors.toMap(DormitoryFaq::getId, Function.identity()));
        assertThat(faqs).hasSize(25);
        Path cacheDirectory = Path.of(".gradle/faq-search-evaluation-cache");
        EmbeddingCache queries = new EmbeddingCache(cacheDirectory.resolve("queries"), LARGE_MODEL);
        EmbeddingCache faqCache = new EmbeddingCache(cacheDirectory.resolve("faq-question-answer"), LARGE_MODEL);
        Function<String, float[]> noApi = text -> { throw new AssertionError("Missing large cache; embedding API is disabled: " + text); };
        List<FaqVector> vectors = new ArrayList<>();
        for (var faq : faqs.values()) {
            float[] cached = faqCache.get("질문: " + faq.getQuestion() + "\n답변: " + faq.getAnswer(), noApi);
            assertThat(faq.isActive()).isTrue();
            assertThat(faq.getEmbedding()).as("Reindex FAQ %d to large before selector evaluation", faq.getId()).isEqualTo(cached);
            vectors.add(new FaqVector(faq.getId(), faq.getQuestion(), cached));
        }
        for (var question : questions) {
            assertThat(faqs.get(question.expectedFaqId()).getQuestion()).isEqualTo(question.originalQuestion());
        }
        Path reports = Path.of("build/reports/faq-selector-evaluation");
        Files.createDirectories(reports);
        int rawCorrect = 0, selectedCorrect = 0, none = 0, wrong = 0, oosNone = 0, calls = 0;
        List<String> improved = new ArrayList<>(), regressed = new ArrayList<>(), stillWrong = new ArrayList<>();
        try (PrintWriter log = writer(reports.resolve("evaluation.log"));
             PrintWriter inDomain = writer(reports.resolve("in-domain.tsv"));
             PrintWriter oos = writer(reports.resolve("oos.tsv"))) {
            log(log, "Run: %s; selector=%s; embedding=%s/1536; L2 Top-3; no threshold%n", Instant.now(), model, LARGE_MODEL);
            inDomain.println("question\texpectedFaqId\tretrievalTop1FaqId\tretrievalTop3FaqIds\tselectorResult\tselectorCorrect\tchange");
            oos.println("question\tretrievalTop1FaqId\tretrievalTop1Distance\tselectorResult\tcorrectNone");
            try {
                for (var question : questions) {
                    var matches = top3(queries.get(question.question(), noApi), vectors);
                    var candidates = candidates(matches, faqs);
                    boolean rawHit = matches.getFirst().id() == question.expectedFaqId();
                    calls++;
                    var selected = selector.select(question.question(), candidates);
                    Long id = selected.map(FaqSearchResult::getId).orElse(null);
                    boolean correct = question.expectedFaqId().equals(id);
                    if (rawHit) rawCorrect++;
                    if (correct) selectedCorrect++;
                    else if (id == null) none++;
                    else wrong++;
                    String change = !rawHit && correct ? "IMPROVED" : rawHit && !correct ? "REGRESSED" : !rawHit ? "STILL WRONG" : "CORRECT";
                    String detail = String.format(Locale.ROOT, "%s | expected=%d, raw=%d, top3=%s, selector=%s",
                            question.question(), question.expectedFaqId(), matches.getFirst().id(), ids(matches), id == null ? "NONE" : id);
                    if (change.equals("IMPROVED")) improved.add(detail);
                    if (change.equals("REGRESSED")) regressed.add(detail);
                    if (change.equals("STILL WRONG")) stillWrong.add(detail);
                    log(log, "[%s] %s%n", change, detail);
                    inDomain.printf(Locale.ROOT, "%s\t%d\t%d\t%s\t%s\t%s\t%s%n", question.question(), question.expectedFaqId(),
                            matches.getFirst().id(), ids(matches), id == null ? "NONE" : id, correct, change);
                    inDomain.flush();
                }
                for (String question : oosQuestions) {
                    var matches = top3(queries.get(question, noApi), vectors);
                    calls++;
                    var selected = selector.select(question, candidates(matches, faqs));
                    Long id = selected.map(FaqSearchResult::getId).orElse(null);
                    if (id == null) oosNone++;
                    log(log, "[OOS] %s | raw=%d, distance=%.9f, selector=%s%n", question,
                            matches.getFirst().id(), matches.getFirst().distance(), id == null ? "NONE" : id);
                    oos.printf(Locale.ROOT, "%s\t%d\t%s\t%s\t%s%n", question, matches.getFirst().id(),
                            matches.getFirst().distance(), id == null ? "NONE" : id, id == null);
                    oos.flush();
                }
            } finally {
                log(log, "Selector API calls=%d (SDK retries excluded); embedding API calls=0%n%s%n%s%n",
                        calls, queries.statistics("Large query"), faqCache.statistics("Large FAQ"));
            }
            String summary = String.format(Locale.ROOT, """
                    ===== FAQ SELECTOR EVALUATION =====
                    Selector model: %s; Responses API; typed Structured Output
                    In-domain questions: %d
                    Raw Retrieval Top-1: %d/%d = %.2f%%
                    Selector Correct: %d
                    Selector Accuracy: %.2f%%
                    Selector NONE Count: %d
                    Selector Wrong FAQ Count: %d
                    Selector recovered: %d
                    Selector regressed: %d
                    Still wrong: %d
                    OOS questions: %d
                    Correct NONE: %d
                    OOS rejection rate: %.2f%%
                    False FAQ matches: %d
                    Overall accuracy: %d/95 = %.2f%%
                    Selector API calls: %d (SDK retries excluded)
                    Embedding API calls: 0
                    %s
                    %s
                    [IMPROVED]
                    %s
                    [REGRESSED]
                    %s
                    [STILL WRONG]
                    %s
                    """, model, questions.size(), rawCorrect, questions.size(), 100.0 * rawCorrect / questions.size(),
                    selectedCorrect, 100.0 * selectedCorrect / questions.size(), none, wrong, improved.size(), regressed.size(),
                    stillWrong.size(), oosQuestions.size(), oosNone, 100.0 * oosNone / oosQuestions.size(), oosQuestions.size() - oosNone,
                    selectedCorrect + oosNone, 100.0 * (selectedCorrect + oosNone) / 95, calls,
                    queries.statistics("Large query"), faqCache.statistics("Large FAQ"), lines(improved), lines(regressed), lines(stillWrong));
            log(log, "%s", summary);
            Files.writeString(reports.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
            assertThat(log.checkError() || inDomain.checkError() || oos.checkError()).isFalse();
        }
    }

    private List<FaqSearchResult> candidates(List<Match> matches, Map<Long, DormitoryFaq> faqs) {
        return matches.stream().map(match -> (FaqSearchResult) new Candidate(match.id(), match.question(),
                faqs.get(match.id()).getAnswer(), match.distance())).toList();
    }

    private record Candidate(Long id, String question, String answer, Double distance) implements FaqSearchResult {
        public Long getId() { return id; }
        public String getQuestion() { return question; }
        public String getAnswer() { return answer; }
        public Double getDistance() { return distance; }
    }

    private static List<Long> ids(List<Match> matches) { return matches.stream().map(Match::id).toList(); }
    private static String lines(List<String> lines) { return lines.isEmpty() ? "None" : String.join("\n", lines); }
    private static PrintWriter writer(Path path) throws IOException { return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8)); }
    private static void log(PrintWriter writer, String format, Object... arguments) {
        System.out.printf(Locale.ROOT, format, arguments);
        writer.printf(Locale.ROOT, format, arguments);
        writer.flush();
    }
}
