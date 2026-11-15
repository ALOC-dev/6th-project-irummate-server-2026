package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
class RecruitmentCursorQueryTest {
    @Autowired
    private RecruitmentsRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(123456000);
    private Long authorId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recruitment_participants");
        jdbc.update("DELETE FROM recruitments");
        jdbc.update("DELETE FROM users");
        authorId = jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES ('cursor-author', 'cursor-author', 'USER', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, now, now);
    }

    @Test
    void createdTimeAndIdCursorHandlesTiesAndNewPostsWithoutRepeatingEarlierResults() {
        Long newest = insert(now.plusSeconds(1), "MEAL", "VISIBLE");
        Long tieFirst = insert(now, "MEAL", "VISIBLE");
        Long tieSecond = insert(now, "MEAL", "VISIBLE");
        Long tieThird = insert(now, "MEAL", "VISIBLE");
        // 가장 큰 ID여도 작성 시각이 오래되면 마지막에 나와야 함
        Long oldest = insert(now.minusDays(1), "MEAL", "VISIBLE");

        var first = find(null, null, null, 2);
        assertEquals(List.of(newest, tieThird), ids(first));
        assertEquals(now, first.getLast().getRecruitment().getCreatedAt());

        insert(now.plusSeconds(2), "MEAL", "VISIBLE");
        var second = find(null, now, tieThird, 2);
        assertEquals(List.of(tieSecond, tieFirst), ids(second));
        assertEquals(List.of(oldest), ids(find(null, now, tieFirst, 2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED"})
    void cursorStillWorksWhenBoundaryPostIsHiddenOrDeleted(String change) {
        insert(now.plusSeconds(1), "MEAL", "VISIBLE");
        Long boundary = insert(now, "MEAL", "VISIBLE");
        Long older = insert(now.minusSeconds(1), "MEAL", "VISIBLE");
        assertEquals(boundary, find(null, null, null, 2).getLast().getRecruitment().getId());

        if (change.equals("HIDDEN")) {
            jdbc.update("UPDATE recruitments SET visibility = 'HIDDEN' WHERE id = ?", boundary);
        } else {
            jdbc.update("DELETE FROM recruitments WHERE id = ?", boundary);
        }

        assertEquals(List.of(older), ids(find(null, now, boundary, 2)));
    }

    @Test
    void visibilityAndCategoryFiltersApplyTogetherWithCursor() {
        Long meal = insert(now, "MEAL", "VISIBLE");
        Long hobby = insert(now.plusSeconds(1), "HOBBY", "VISIBLE");
        insert(now.plusSeconds(2), "MEAL", "HIDDEN");
        Long olderMeal = insert(now.minusSeconds(1), "MEAL", "VISIBLE");

        assertEquals(List.of(meal, olderMeal), ids(find(RecruitmentCategory.MEAL, null, null, 3)));
        assertEquals(List.of(hobby, meal, olderMeal), ids(find(null, null, null, 3)));
        assertEquals(List.of(olderMeal), ids(find(RecruitmentCategory.MEAL, now, meal, 3)));
    }

    @Test
    void listingDoesNotExecuteAnAdditionalCountQuery() {
        insert(now, "MEAL", "VISIBLE");
        insert(now.minusSeconds(1), "MEAL", "VISIBLE");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            assertEquals(1, find(null, null, null, 1).size());
            assertEquals(1, statistics.getQueryExecutionCount());
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private List<RecruitmentsRepository.RecruitmentListRow> find(
            RecruitmentCategory category, LocalDateTime cursorTime, Long cursorId, int limit
    ) {
        return new TransactionTemplate(transactionManager).execute(tx -> repository.findRecruitmentList(
                authorId, category, RecruitmentVisibility.VISIBLE, cursorTime, cursorId, PageRequest.of(0, limit)));
    }

    private List<Long> ids(List<RecruitmentsRepository.RecruitmentListRow> rows) {
        return rows.stream().map(row -> row.getRecruitment().getId()).toList();
    }

    private Long insert(LocalDateTime createdAt, String category, String visibility) {
        return jdbc.queryForObject("""
                INSERT INTO recruitments (
                    author_id, title, category, status, visibility, activity_at, deadline_at,
                    now_participants, max_participants, created_at, updated_at
                ) VALUES (?, 'cursor-test', ?, 'OPEN', ?, ?, ?, 1, 3, ?, ?) RETURNING id
                """, Long.class, authorId, category, visibility, now.plusDays(2), now.plusDays(1), createdAt, createdAt);
    }
}
