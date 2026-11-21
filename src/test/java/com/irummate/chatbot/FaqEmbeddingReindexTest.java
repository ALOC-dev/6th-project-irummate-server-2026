package com.irummate.chatbot;

import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import static com.irummate.chatbot.FaqEmbeddingEvaluationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 명시적으로 실행할 때만 개발 DB를 갱신한다. 모든 검증 성공 시 한 번에 commit한다. */
@EnabledIfEnvironmentVariable(named = "FAQ_EMBEDDING_REINDEX_TEST", matches = "true")
@SpringBootTest(classes = FaqSearchLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
class FaqEmbeddingReindexTest {
    @Autowired DormitoryFaqRepository repository;
    @Autowired EmbeddingService embeddingService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void reindexesDevelopmentFaqsWithLarge1536InOneTransaction() throws IOException {
        Path directory = Path.of(".gradle/faq-search-evaluation-cache");
        EmbeddingCache faqs = new EmbeddingCache(directory.resolve("faq-question-answer"), LARGE_MODEL);
        EmbeddingCache queries = new EmbeddingCache(directory.resolve("queries"), LARGE_MODEL);
        Path backup = directory.resolve("reindex-backups/" + Instant.now().toEpochMilli() + ".tsv");
        String summary = new TransactionTemplate(transactionManager).execute(status -> {
            try {
                jdbc.queryForList("SELECT id FROM dormitory_faq ORDER BY id FOR UPDATE");
                var entries = repository.findAll();
                assertThat(entries).hasSize(25);
                String metadata = metadataFingerprint();
                StringBuilder previous = new StringBuilder("id\tpreviousEmbedding\n");
                jdbc.queryForList("SELECT id, embedding::text AS vector FROM dormitory_faq ORDER BY id")
                        .forEach(row -> previous.append(row.get("id")).append('\t').append(row.get("vector")).append('\n'));
                Files.createDirectories(backup.getParent());
                Files.writeString(backup, previous, StandardCharsets.UTF_8);
                int changed = 0;
                for (var faq : entries) {
                    String text = "질문: " + faq.getQuestion() + "\n답변: " + faq.getAnswer();
                    float[] vector = faqs.get(text, embeddingService::embedToArray);
                    changed += jdbc.update("""
                            UPDATE dormitory_faq SET embedding = CAST(? AS vector)
                            WHERE id = ? AND embedding IS DISTINCT FROM CAST(? AS vector)
                            """, Arrays.toString(vector), faq.getId(), Arrays.toString(vector));
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_faq", Integer.class)).isEqualTo(25);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_faq WHERE embedding IS NULL OR vector_dims(embedding) <> 1536", Integer.class)).isZero();
                assertThat(metadataFingerprint()).isEqualTo(metadata);
                float[] query = queries.get("기숙사 와이파이 돼?", embeddingService::embedToArray);
                var results = repository.findTop3ByEmbedding(Arrays.toString(query));
                assertThat(results).hasSize(3);
                assertThat(results.getFirst().getId()).isEqualTo(9L);
                return "Committed large/1536 reindex: FAQ count=25, null=0, wrong dimension=0, changed=" + changed
                        + "\nMetadata unchanged; backup=" + backup.toAbsolutePath()
                        + "\nActual pgvector wifi Top-1=" + results.getFirst().getId() + ", distance=" + results.getFirst().getDistance()
                        + "\n" + faqs.statistics("Large FAQ") + "\n" + queries.statistics("Large query") + "\n";
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        Path report = Path.of("build/reports/faq-embedding-reindex/summary.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, summary, StandardCharsets.UTF_8);
        System.out.println(summary);
    }

    private String metadataFingerprint() {
        return jdbc.queryForObject("SELECT md5(string_agg((id,category,question,answer,is_active)::text, '|' ORDER BY id)) FROM dormitory_faq", String.class);
    }
}
