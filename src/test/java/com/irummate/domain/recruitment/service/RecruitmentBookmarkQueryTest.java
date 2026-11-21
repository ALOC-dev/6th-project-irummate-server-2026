package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = RecruitmentLifecycleBatchTest.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.sql.init.mode=never"
        }
)
class RecruitmentBookmarkQueryTest {
    @Autowired
    private RecruitmentBookmarksRepository bookmarks;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
    private Long firstUser;
    private Long secondUser;
    private Long recruitmentId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recruitment_bookmarks");
        jdbc.update("DELETE FROM recruitment_participants");
        jdbc.update("DELETE FROM recruitments");
        jdbc.update("DELETE FROM users");
        firstUser = insertUser("bookmark-first");
        secondUser = insertUser("bookmark-second");
        recruitmentId = jdbc.queryForObject("""
                INSERT INTO recruitments (
                    author_id, title, category, status, visibility, activity_at, deadline_at,
                    now_participants, max_participants, created_at, updated_at
                ) VALUES (?, 'bookmark-test', 'MEAL', 'OPEN', 'VISIBLE', ?, ?, 1, 3, ?, ?) RETURNING id
                """, Long.class, firstUser, now.plusDays(2), now.plusDays(1), now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM recruitment_bookmarks");
    }

    @Test
    void duplicateRegistrationPreservesOriginalTime() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int inserted = tx.execute(status -> bookmarks.insertIfAbsent(firstUser, recruitmentId, now));
        int duplicate = tx.execute(status -> bookmarks.insertIfAbsent(firstUser, recruitmentId, now.plusMinutes(1)));
        assertEquals(1, inserted);
        assertEquals(0, duplicate);

        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_bookmarks", Long.class));
        assertEquals(Timestamp.valueOf(now), jdbc.queryForObject(
                "SELECT bookmarked_at FROM recruitment_bookmarks", Timestamp.class));
    }

    @Test
    void concurrentRegistrationCommitsOnlyOneBookmark() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 대기 초과");
                }
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setTimeout(10);
                return tx.execute(status -> bookmarks.insertIfAbsent(firstUser, recruitmentId, now));
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_bookmarks", Long.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void removalIsScopedToOwnerAndReaddingCreatesNewRecord() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            bookmarks.insertIfAbsent(firstUser, recruitmentId, now);
            bookmarks.insertIfAbsent(secondUser, recruitmentId, now);
        });
        Long previousId = jdbc.queryForObject(
                "SELECT id FROM recruitment_bookmarks WHERE user_id = ?", Long.class, firstUser);

        int removed = tx.execute(status -> bookmarks.deleteBookmark(firstUser, recruitmentId));
        int repeatedRemoval = tx.execute(status -> bookmarks.deleteBookmark(firstUser, recruitmentId));
        assertEquals(1, removed);
        assertEquals(0, repeatedRemoval);
        assertEquals(secondUser, jdbc.queryForObject("SELECT user_id FROM recruitment_bookmarks", Long.class));

        tx.executeWithoutResult(status -> bookmarks.insertIfAbsent(firstUser, recruitmentId, now.plusHours(1)));
        assertNotEquals(previousId, jdbc.queryForObject(
                "SELECT id FROM recruitment_bookmarks WHERE user_id = ?", Long.class, firstUser));
        assertEquals(Timestamp.valueOf(now.plusHours(1)), jdbc.queryForObject(
                "SELECT bookmarked_at FROM recruitment_bookmarks WHERE user_id = ?", Timestamp.class, firstUser));
    }

    @Test
    void listFiltersOwnerAndHiddenPostsAndFetchesParticipationAndAuthorInOneQuery() {
        insertBookmark(firstUser, recruitmentId, now.minusMinutes(3));
        Long closed = insertPost(secondUser, "CLOSED", "VISIBLE", now.minusDays(4));
        Long cancelled = insertPost(secondUser, "CANCELLED", "VISIBLE", now.minusDays(3));
        Long ended = insertPost(secondUser, "ENDED", "VISIBLE", now.minusDays(2));
        insertBookmark(firstUser, closed, now.minusMinutes(2));
        insertBookmark(firstUser, cancelled, now.minusMinutes(1));
        insertBookmark(firstUser, ended, now);
        insertBookmark(firstUser, insertPost(firstUser, "OPEN", "HIDDEN", now), now.plusMinutes(1));
        insertBookmark(secondUser, insertPost(secondUser, "OPEN", "VISIBLE", now), now.plusMinutes(2));
        jdbc.update("""
                INSERT INTO recruitment_participants (
                    recruitment_id, user_id, role, status, joined_at, created_at, updated_at
                ) VALUES (?, ?, 'MEMBER', 'LEFT', ?, ?, ?)
                """, closed, firstUser, now, now, now);

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            RecruitmentBookmarkService service = new RecruitmentBookmarkService(
                    bookmarks, mock(RecruitmentsRepository.class), mock(UsersRepository.class), Clock.system(ZoneId.of("Asia/Seoul")));
            var response = new TransactionTemplate(transactionManager).execute(tx ->
                    service.getMyBookmarks(firstUser, null, null, 10));

            assertEquals(List.of(ended, cancelled, closed, recruitmentId),
                    response.getItems().stream().map(item -> item.getRecruitmentId()).toList());
            assertEquals(List.of(RecruitmentParticipationView.NONE, RecruitmentParticipationView.NONE,
                            RecruitmentParticipationView.LEFT, RecruitmentParticipationView.MINE),
                    response.getItems().stream().map(item -> item.getParticipating()).toList());
            assertEquals("bookmark-second", response.getItems().getFirst().getAuthorNickname());
            assertFalse(response.isHasNext());
            assertEquals(1, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    void cursorUsesBookmarkTimeAndIdDespitePostOrderAndNewBookmarks() {
        Long older = insertPost(firstUser, "OPEN", "VISIBLE", now.plusHours(3));
        Long tieFirst = insertPost(firstUser, "OPEN", "VISIBLE", now.plusHours(2));
        Long tieSecond = insertPost(firstUser, "OPEN", "VISIBLE", now.plusHours(1));
        Long olderBookmark = insertBookmark(firstUser, older, now.minusSeconds(1));
        Long firstTieBookmark = insertBookmark(firstUser, tieFirst, now);
        Long secondTieBookmark = insertBookmark(firstUser, tieSecond, now);
        Long newestBookmark = insertBookmark(firstUser, recruitmentId, now.plusSeconds(1));

        var firstPage = find(null, null, 2);
        assertEquals(List.of(newestBookmark, secondTieBookmark), bookmarkIds(firstPage));
        insertBookmark(firstUser, insertPost(firstUser, "OPEN", "VISIBLE", now), now.plusSeconds(2));

        assertEquals(List.of(firstTieBookmark, olderBookmark), bookmarkIds(find(now, secondTieBookmark, 2)));
    }

    @Test
    void cursorStillWorksAfterBoundaryBookmarkIsRemoved() {
        Long older = insertPost(firstUser, "OPEN", "VISIBLE", now);
        Long boundary = insertBookmark(firstUser, recruitmentId, now);
        Long olderBookmark = insertBookmark(firstUser, older, now.minusSeconds(1));
        jdbc.update("DELETE FROM recruitment_bookmarks WHERE id = ?", boundary);

        assertEquals(List.of(olderBookmark), bookmarkIds(find(now, boundary, 2)));
    }

    private List<RecruitmentBookmarksRepository.RecruitmentBookmarkRow> find(
            LocalDateTime cursorTime, Long cursorId, int limit
    ) {
        return new TransactionTemplate(transactionManager).execute(tx -> bookmarks.findMyBookmarks(
                firstUser, RecruitmentVisibility.VISIBLE, cursorTime, cursorId, PageRequest.of(0, limit)));
    }

    private List<Long> bookmarkIds(List<RecruitmentBookmarksRepository.RecruitmentBookmarkRow> rows) {
        return rows.stream().map(row -> row.getBookmarkId()).toList();
    }

    private Long insertBookmark(Long userId, Long postId, LocalDateTime time) {
        return jdbc.queryForObject("""
                INSERT INTO recruitment_bookmarks (user_id, recruitment_id, bookmarked_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, userId, postId, time);
    }

    private Long insertPost(Long authorId, String status, String visibility, LocalDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO recruitments (
                    author_id, title, category, status, visibility, activity_at, deadline_at,
                    now_participants, max_participants, created_at, updated_at
                ) VALUES (?, 'bookmark-list-test', 'MEAL', ?, ?, ?, ?, 1, 3, ?, ?) RETURNING id
                """, Long.class, authorId, status, visibility, now.plusDays(2), now.plusDays(1), createdAt, createdAt);
    }

    private Long insertUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'USER', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, name, name, now, now);
    }
}
