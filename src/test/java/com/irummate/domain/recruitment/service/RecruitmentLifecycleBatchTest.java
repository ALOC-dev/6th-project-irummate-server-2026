package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = RecruitmentLifecycleBatchTest.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.sql.init.mode=never"
        }
)
class RecruitmentLifecycleBatchTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.irummate.domain")
    @EnableJpaRepositories(basePackageClasses = RecruitmentsRepository.class)
    // 생성/수정 시각 자동 기록(JpaAuditingConfig)과 서비스가 쓰는 Clock 등록
    @Import({RecruitmentLifecycleService.class, JpaAuditingConfig.class})
    static class PersistenceConfiguration {
        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                    .withInitScript("db/test-init.sql");
        }
    }

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
    private static final LocalDateTime OLD = NOW.minusDays(2);

    @Autowired
    private RecruitmentsRepository repository;
    @Autowired
    private RecruitmentLifecycleService lifecycleService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long authorId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recruitment_participants");
        jdbc.update("DELETE FROM recruitments");
        jdbc.update("DELETE FROM users");
        authorId = jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES ('batch-test', 'batch-test', 'USER', 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, OLD, OLD);
    }

    @Test
    void endingPreservesExistingClosureAndExcludesInactiveOrFuturePosts() {
        Long open = insert("OPEN", NOW, NOW.minusHours(1), null, null);
        Long closed = insert("CLOSED", NOW.minusMinutes(1), NOW.minusHours(1), "FULL", OLD);
        Long cancelled = insert("CANCELLED", NOW, NOW.minusHours(1), null, null);
        Long ended = insert("ENDED", NOW, NOW.minusHours(1), null, null);
        Long future = insert("OPEN", NOW.plusHours(1), NOW.plusMinutes(30), null, null);

        assertEquals(2, runBatch(true, 50));

        Map<String, Object> openRow = row(open);
        assertEquals("ENDED", openRow.get("status"));
        assertEquals(Timestamp.valueOf(NOW), openRow.get("closed_at"));
        assertEquals(Timestamp.valueOf(NOW), openRow.get("completed_at"));
        assertEquals(Timestamp.valueOf(NOW), openRow.get("updated_at"));
        assertNull(openRow.get("close_reason"));
        assertEquals(1, openRow.get("now_participants"));

        Map<String, Object> closedRow = row(closed);
        assertEquals("ENDED", closedRow.get("status"));
        assertEquals("FULL", closedRow.get("close_reason"));
        assertEquals(Timestamp.valueOf(OLD), closedRow.get("closed_at"));
        assertEquals(Timestamp.valueOf(NOW), closedRow.get("completed_at"));
        assertEquals(Timestamp.valueOf(NOW), closedRow.get("updated_at"));

        assertEquals("CANCELLED", row(cancelled).get("status"));
        assertEquals(Timestamp.valueOf(OLD), row(ended).get("updated_at"));
        assertEquals("OPEN", row(future).get("status"));
        assertEquals(0, runBatch(true, 50));
    }

    @Test
    void deadlineClosureExcludesStartedInactiveAndAlreadyClosedPosts() {
        Long target = insert("OPEN", NOW.plusHours(1), NOW, null, null);
        Long started = insert("OPEN", NOW, NOW.minusHours(1), null, null);
        Long futureDeadline = insert("OPEN", NOW.plusHours(2), NOW.plusHours(1), null, null);
        Long closed = insert("CLOSED", NOW.plusHours(1), NOW, "MANUAL", OLD);
        Long cancelled = insert("CANCELLED", NOW.plusHours(1), NOW, null, null);
        Long ended = insert("ENDED", NOW.plusHours(1), NOW, null, null);

        assertEquals(1, runBatch(false, 50));

        Map<String, Object> targetRow = row(target);
        assertEquals("CLOSED", targetRow.get("status"));
        assertEquals("DEADLINE", targetRow.get("close_reason"));
        assertEquals(Timestamp.valueOf(NOW), targetRow.get("closed_at"));
        assertEquals(Timestamp.valueOf(NOW), targetRow.get("updated_at"));
        assertNull(targetRow.get("completed_at"));
        assertEquals(1, targetRow.get("now_participants"));

        assertEquals("OPEN", row(started).get("status"));
        assertEquals("OPEN", row(futureDeadline).get("status"));
        assertEquals("MANUAL", row(closed).get("close_reason"));
        assertEquals(Timestamp.valueOf(OLD), row(closed).get("closed_at"));
        assertEquals("CANCELLED", row(cancelled).get("status"));
        assertEquals(Timestamp.valueOf(OLD), row(ended).get("updated_at"));
        assertEquals(0, runBatch(false, 50));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void batchesPrioritizeOverdueTimeThenIdAndLimitToFifty(boolean ending) {
        Long newer = insertTarget(ending, NOW.minusMinutes(1));
        List<Long> older = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            older.add(insertTarget(ending, NOW.minusHours(1)));
        }

        // 실제 서비스 진입점도 호출해 50건 제한과 트랜잭션 설정을 함께 검증
        int count = ending ? lifecycleService.endRecruitments() : lifecycleService.closeRecruitmentsByDeadline();
        assertEquals(50, count);
        String expectedStatus = ending ? "ENDED" : "CLOSED";
        for (int i = 0; i < 50; i++) {
            assertEquals(expectedStatus, row(older.get(i)).get("status"));
        }
        assertEquals("OPEN", row(older.get(50)).get("status"));
        assertEquals("OPEN", row(newer).get("status"));
        assertEquals(2, runBatch(ending, 50));
        assertEquals(0, runBatch(ending, 50));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void skipsLockedPostAndProcessesItAfterLockIsReleased(boolean ending) throws Exception {
        Long locked = insertTarget(ending, NOW.minusHours(2));
        Long available = insertTarget(ending, NOW.minusHours(1));

        // 별도 커넥션으로 실제 행 잠금을 유지한 상태에서 일괄 UPDATE 실행
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM recruitments WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, locked);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                }
            }

            assertEquals(1, runBatch(ending, 1));
            assertEquals("OPEN", row(locked).get("status"));
            assertEquals(ending ? "ENDED" : "CLOSED", row(available).get("status"));
            assertEquals(0, runBatch(ending, 1));
            connection.commit();
        }

        assertEquals(1, runBatch(ending, 1));
        assertEquals(ending ? "ENDED" : "CLOSED", row(locked).get("status"));
        assertEquals(0, runBatch(ending, 1));
    }

    @Test
    void listProjectionPreservesViewerParticipationAndAuthorRelationship() {
        Long otherAuthor = jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES ('other-author', 'other-author', 'USER', 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, OLD, OLD);
        Long mine = insertTarget(false, NOW.minusHours(1));
        Long noHistory = insertTarget(false, NOW.minusHours(1));
        jdbc.update("UPDATE recruitments SET author_id = ? WHERE id = ?", otherAuthor, noHistory);
        Map<Long, RecruitmentParticipationView> expected = new HashMap<>();
        expected.put(mine, RecruitmentParticipationView.MINE);
        expected.put(noHistory, RecruitmentParticipationView.NONE);

        for (RecruitmentParticipantStatus status : RecruitmentParticipantStatus.values()) {
            Long id = insertTarget(false, NOW.minusHours(1));
            jdbc.update("UPDATE recruitments SET author_id = ? WHERE id = ?", otherAuthor, id);
            jdbc.update("""
                    INSERT INTO recruitment_participants (
                        recruitment_id, user_id, role, status, joined_at, created_at, updated_at
                    ) VALUES (?, ?, 'MEMBER', ?, ?, ?, ?)
                    """, id, authorId, status.name(), OLD, OLD, OLD);
            expected.put(id, RecruitmentParticipationView.from(false, status));
        }

        Map<Long, RecruitmentParticipationView> actual = new TransactionTemplate(transactionManager).execute(tx -> {
            Map<Long, RecruitmentParticipationView> result = new HashMap<>();
            repository.findRecruitmentList(authorId, RecruitmentCategory.MEAL,
                            RecruitmentVisibility.VISIBLE, null, null, PageRequest.of(0, 10))
                    .forEach(row -> result.put(row.getRecruitment().getId(), RecruitmentParticipationView.from(
                            row.getRecruitment().getAuthor().getId().equals(authorId), row.getParticipantStatus())));
            return result;
        });

        assertEquals(expected, actual);
    }

    @Test
    void calculatedDeadlineClosesThirtyMinutesBeforeActivityAndEndsAtActivity() {
        Recruitments recruitment = Recruitments.builder().build();
        recruitment.configureSchedule(NOW.plusHours(1), NOW);
        Long id = insert("OPEN", recruitment.getActivityAt(), recruitment.getDeadlineAt(), null, null);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        int beforeDeadline = tx.execute(status -> repository.closeRecruitmentsByDeadlineInBatch(
                NOW.plusMinutes(30).minusSeconds(1), 50));
        int atDeadline = tx.execute(status -> repository.closeRecruitmentsByDeadlineInBatch(NOW.plusMinutes(30), 50));
        assertEquals(0, beforeDeadline);
        assertEquals(1, atDeadline);
        assertEquals("CLOSED", row(id).get("status"));
        assertEquals("DEADLINE", row(id).get("close_reason"));

        int beforeActivity = tx.execute(status -> repository.endRecruitmentsInBatch(NOW.plusHours(1).minusSeconds(1), 50));
        int atActivity = tx.execute(status -> repository.endRecruitmentsInBatch(NOW.plusHours(1), 50));
        assertEquals(0, beforeActivity);
        assertEquals(1, atActivity);
        assertEquals("ENDED", row(id).get("status"));
        assertEquals(Timestamp.valueOf(NOW.plusMinutes(30)), row(id).get("closed_at"));
        assertEquals(Timestamp.valueOf(NOW.plusHours(1)), row(id).get("completed_at"));
    }

    private int runBatch(boolean ending, int batchSize) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            // SKIP LOCKED가 빠지면 오래 대기하는 대신 테스트가 실패하도록 제한
            jdbc.execute("SET LOCAL lock_timeout = '200ms'");
            return ending ? repository.endRecruitmentsInBatch(NOW, batchSize)
                    : repository.closeRecruitmentsByDeadlineInBatch(NOW, batchSize);
        });
    }

    private Long insertTarget(boolean ending, LocalDateTime dueAt) {
        return ending ? insert("OPEN", dueAt, dueAt.minusHours(1), null, null)
                : insert("OPEN", NOW.plusDays(1), dueAt, null, null);
    }

    private Long insert(String status, LocalDateTime activityAt, LocalDateTime deadlineAt,
                        String closeReason, LocalDateTime closedAt) {
        return jdbc.queryForObject("""
                INSERT INTO recruitments (
                    author_id, title, category, status, visibility, activity_at, deadline_at,
                    now_participants, max_participants, created_at, updated_at, close_reason, closed_at
                ) VALUES (?, 'batch-test', 'MEAL', ?, 'VISIBLE', ?, ?, 1, 3, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, authorId, status, activityAt, deadlineAt, OLD, OLD, closeReason, closedAt);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM recruitments WHERE id = ?", id);
    }
}
