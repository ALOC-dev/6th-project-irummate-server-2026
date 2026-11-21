package com.irummate.domain.recruitment.service;

import com.irummate.domain.chat.service.RecruitmentChatRoomService;

import com.irummate.domain.recruitment.dto.RecruitmentSort;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

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
class RecruitmentCursorQueryTest {
    @Autowired
    private RecruitmentsRepository repository;
    @Autowired
    private RecruitmentBookmarksRepository bookmarks;
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
        jdbc.update("DELETE FROM recruitment_bookmarks");
        jdbc.update("DELETE FROM recruitment_participants");
        jdbc.update("DELETE FROM recruitments");
        jdbc.update("DELETE FROM users");
        authorId = jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES ('cursor-author', 'cursor-author', 'USER', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, now, now);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM recruitment_bookmarks");
    }

    @ParameterizedTest
    @EnumSource(RecruitmentSort.class)
    void bookmarkFlagsAreUserScopedWithoutDuplicatesOrAdditionalQueriesForEachPost(RecruitmentSort sort) {
        Long otherAuthor = jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, role, status, created_at, updated_at)
                VALUES ('cursor-other', 'other-author', 'USER', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, now, now);
        List<Long> posts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Long postId = insert(now.plusSeconds(i), "MEAL", "VISIBLE");
            posts.add(postId);
            if (i % 2 == 0) {
                jdbc.update("UPDATE recruitments SET author_id = ? WHERE id = ?", otherAuthor, postId);
            }
            // 다른 사용자의 즐겨찾기가 있어도 내 목록의 행 수와 상태는 바뀌지 않아야 함
            jdbc.update("INSERT INTO recruitment_bookmarks (user_id, recruitment_id, bookmarked_at) VALUES (?, ?, ?)",
                    otherAuthor, postId, now);
        }
        Set<Long> saved = Set.of(posts.getFirst(), posts.getLast());
        for (Long postId : saved) {
            jdbc.update("INSERT INTO recruitment_bookmarks (user_id, recruitment_id, bookmarked_at) VALUES (?, ?, ?)",
                    authorId, postId, now);
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            RecruitmentService service = new RecruitmentService(repository, mock(RecruitmentParticipantsRepository.class),
                    mock(UsersRepository.class), bookmarks, mock(RecruitmentChatRoomService.class));
            var response = new TransactionTemplate(transactionManager).execute(tx ->
                    service.getRecruitments(authorId, null, sort, null, null, null, 50));

            assertEquals(20, response.getItems().size());
            assertEquals(20, response.getItems().stream().map(item -> item.getRecruitmentId()).distinct().count());
            assertFalse(response.isHasNext());
            response.getItems().forEach(item -> assertEquals(saved.contains(item.getRecruitmentId()), item.isBookmarked()));
            assertEquals("other-author", response.getItems().get(1).getAuthorNickname());
            assertEquals(1, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }

        // 참여 이력에서 사용하는 일괄 조회도 현재 사용자 기록만 반환
        assertEquals(saved, Set.copyOf(new TransactionTemplate(transactionManager).execute(tx ->
                bookmarks.findBookmarkedRecruitmentIds(authorId, posts))));
        assertTrue(bookmarks.existsByUser_IdAndRecruitment_Id(authorId, posts.getFirst()));
        assertFalse(bookmarks.existsByUser_IdAndRecruitment_Id(authorId, posts.get(1)));
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

    @Test
    void closingSoonFiltersTimeOrCapacityAtExactBoundariesBeforeApplyingLimit() {
        Long withinTime = insertClosing(now.plusHours(1), 1, 10);
        Long at24Hours = insertClosing(now.plusHours(24), 1, 10);
        insertClosing(now.plusHours(24).plusNanos(1000), 1, 10);
        Long at80Percent = insertClosing(now.plusDays(7), 4, 5);
        insertClosing(now.plusDays(7), 799, 1000);
        Long largeCapacity = insertClosing(now.plusDays(7), 1_600_000_000, 2_000_000_000);
        insertClosing(now.plusDays(7), 1_599_999_999, 2_000_000_000);
        insertClosing(now, 4, 5);
        insertClosing(now.minusSeconds(1), 4, 5);
        insertClosing(now.plusMinutes(30), 5, 5);

        for (String status : List.of("CLOSED", "CANCELLED", "ENDED")) {
            Long excluded = insertClosing(now.plusMinutes(10), 4, 5);
            jdbc.update("UPDATE recruitments SET status = ? WHERE id = ?", status, excluded);
        }
        Long hidden = insertClosing(now.plusMinutes(10), 4, 5);
        jdbc.update("UPDATE recruitments SET visibility = 'HIDDEN' WHERE id = ?", hidden);
        Long activityStarted = insertClosing(now.plusMinutes(10), 4, 5);
        jdbc.update("UPDATE recruitments SET activity_at = ? WHERE id = ?", now, activityStarted);

        assertEquals(List.of(withinTime, at24Hours, largeCapacity, at80Percent), ids(findClosing(null, null, null, 50)));
        assertEquals(List.of(withinTime, at24Hours), ids(findClosing(null, null, null, 2)));
    }

    @Test
    void closingSoonCursorUsesAscendingDeadlineAndDescendingIdDespiteNewEarlierPosts() {
        Long first = insertClosing(now.plusHours(1), 1, 10);
        Long tieFirst = insertClosing(now.plusHours(2), 1, 10);
        Long tieSecond = insertClosing(now.plusHours(2), 1, 10);
        Long last = insertClosing(now.plusHours(3), 1, 10);

        assertEquals(List.of(first, tieSecond), ids(findClosing(null, null, null, 2)));
        insertClosing(now.plusMinutes(30), 1, 10);
        assertEquals(List.of(tieFirst, last), ids(findClosing(null, now.plusHours(2), tieSecond, 2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED"})
    void closingSoonCategoryAndCursorWorkAfterBoundaryPostDisappears(String change) {
        Long boundary = insertClosing(now.plusHours(1), 1, 10);
        Long hobby = insertClosing(now.plusHours(2), 1, 10);
        jdbc.update("UPDATE recruitments SET category = 'HOBBY' WHERE id = ?", hobby);
        Long laterMeal = insertClosing(now.plusHours(3), 1, 10);
        assertEquals(List.of(boundary, laterMeal), ids(findClosing(RecruitmentCategory.MEAL, null, null, 2)));

        if (change.equals("HIDDEN")) {
            jdbc.update("UPDATE recruitments SET visibility = 'HIDDEN' WHERE id = ?", boundary);
        } else {
            jdbc.update("DELETE FROM recruitments WHERE id = ?", boundary);
        }

        assertEquals(List.of(laterMeal), ids(findClosing(RecruitmentCategory.MEAL, now.plusHours(1), boundary, 2)));
    }

    private List<RecruitmentsRepository.RecruitmentListRow> findClosing(
            RecruitmentCategory category, LocalDateTime cursorTime, Long cursorId, int limit
    ) {
        return new TransactionTemplate(transactionManager).execute(tx -> repository.findClosingSoonRecruitmentList(
                authorId, category, RecruitmentVisibility.VISIBLE, RecruitmentStatus.OPEN,
                now, now.plusHours(24), cursorTime, cursorId, PageRequest.of(0, limit)));
    }

    private Long insertClosing(LocalDateTime deadline, int count, int capacity) {
        Long id = insert(now.minusDays(1), "MEAL", "VISIBLE");
        jdbc.update("UPDATE recruitments SET deadline_at = ?, activity_at = ?, now_participants = ?, max_participants = ? WHERE id = ?",
                deadline, deadline.plusHours(1), count, capacity, id);
        return id;
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
