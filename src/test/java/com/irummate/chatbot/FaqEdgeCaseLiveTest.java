package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import com.irummate.domain.chatbot.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@EnabledIfEnvironmentVariable(named = "FAQ_EDGE_LIVE_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@Import({FaqChatService.class, FaqSelectionService.class, FaqAnswerService.class})
class FaqEdgeCaseLiveTest {
    private static final Path REPORT = Path.of("build/reports/faq-edge-eval");
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired private FaqChatService chat;
    @Autowired private DormitoryFaqRepository repository;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private EmbeddingService embedding;
    @MockitoSpyBean private FaqSearchService search;
    @MockitoSpyBean private FaqSelectionService selector;
    @MockitoSpyBean private FaqAnswerService generator;

    @Test
    @Transactional(readOnly = true)
    void evaluate() throws Exception {
        Files.createDirectories(REPORT);
        var before = snapshot();
        assertThat(before).hasSize(25);
        Path baseline = REPORT.resolve("faq-source.json");
        if (Files.exists(baseline)) {
            assertThat(json.readTree(Files.readString(baseline)))
                    .isEqualTo(json.readTree(json.writeValueAsString(before)));
        } else {
            write(baseline, before);
        }

        int limit = Integer.parseInt(System.getenv().getOrDefault("FAQ_EDGE_LIMIT", "90"));
        if (limit == 0) return;
        JsonNode cases;
        try (var resource = getClass().getResourceAsStream("/chatbot/faq-edge-cases.json")) {
            cases = json.readTree(Objects.requireNonNull(resource));
        }
        assertThat(cases.size()).isEqualTo(100);
        Path rows = REPORT.resolve("live");
        Files.createDirectories(rows);
        var dataset = json.writerWithDefaultPrettyPrinter().writeValueAsString(cases);
        Path frozen = REPORT.resolve("test-cases.json");
        if (Files.exists(frozen)) {
            assertThat(json.readTree(Files.readString(frozen))).isEqualTo(cases);
        } else {
            Files.writeString(frozen, dataset);
        }
        int maxNew = Math.min(limit, 90);
        int executed = 0;
        int[] counts = new int[3];
        for (int i = 0; i < 90 && executed < maxNew; i++) {
            JsonNode test = cases.get(i);
            String id = test.get("id").asText();
            Path result = rows.resolve(id + ".json");
            if (Files.exists(result)) continue;
            Trace trace = new Trace();
            observe(trace);
            try {
                trace.answer = chat.chat(test.get("question").asText());
            } catch (RuntimeException exception) {
                trace.error = exception.getClass().getSimpleName();
            }
            write(result, trace);
            counts[0] += trace.embeddingCalls;
            counts[1] += trace.selectorCalls;
            counts[2] += trace.answerCalls;
            executed++;
            System.out.printf("%s top=%s selected=%s error=%s%n", id, trace.candidates, trace.selected, trace.error);
        }
        entityManager.clear();
        assertThat(snapshot()).isEqualTo(before);
        System.out.printf("NEW=%d embedding=%d selector=%d answer=%d%n", executed, counts[0], counts[1], counts[2]);
    }

    private void observe(Trace trace) {
        doAnswer(call -> {
            trace.embeddingCalls++;
            return call.callRealMethod();
        }).when(embedding).embed(anyString());
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            List<FaqSearchResult> candidates = (List<FaqSearchResult>) call.callRealMethod();
            trace.candidates = candidates.stream().map(faq -> new Candidate(faq.getId(), faq.getDistance())).toList();
            return candidates;
        }).when(search).search(anyString());
        doAnswer(call -> {
            trace.selectorCalls++;
            @SuppressWarnings("unchecked")
            Optional<FaqSearchResult> selected = (Optional<FaqSearchResult>) call.callRealMethod();
            trace.selected = selected.map(FaqSearchResult::getId).orElse(null);
            return selected;
        }).when(selector).select(anyString(), anyList());
        doAnswer(call -> {
            trace.answerCalls++;
            return call.callRealMethod();
        }).when(generator).generateAnswer(anyString(), any(FaqSearchResult.class));
    }

    private List<Source> snapshot() {
        return repository.findAll().stream().sorted(Comparator.comparing(faq -> faq.getId())).map(faq -> {
            assertThat(faq.isActive()).isTrue();
            FaqEmbeddingEvaluationSupport.validate(faq.getEmbedding());
            return new Source(faq.getId(), faq.getCategory().name(), faq.getQuestion(), faq.getAnswer(),
                    java.util.HexFormat.of().formatHex(digest(faq.getEmbedding())));
        }).toList();
    }

    private byte[] digest(float[] embedding) {
        var bytes = java.nio.ByteBuffer.allocate(embedding.length * Float.BYTES);
        for (float value : embedding) bytes.putFloat(value);
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void write(Path target, Object value) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "edge-", ".tmp");
        try {
            Files.writeString(temp, json.writerWithDefaultPrettyPrinter().writeValueAsString(value));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static class Trace {
        public List<Candidate> candidates = List.of();
        public Long selected;
        public String answer;
        public String error;
        public int embeddingCalls;
        public int selectorCalls;
        public int answerCalls;
    }

    record Candidate(Long id, Double distance) {}
    record Source(Long id, String category, String question, String answer, String embeddingSha256) {}
}
