package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantRole;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class RecruitmentParticipantQueryTest {
    @Autowired
    private RecruitmentParticipantsRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);

    @Test
    void joinedParticipantsAreOrderedByJoinTimeAndIdUsingOneScalarQuery() {
        jdbc.update("DELETE FROM recruitment_participants");
        jdbc.update("DELETE FROM recruitments");
        jdbc.update("DELETE FROM users");
        Long author = insertUser("author");
        Long earlyMember = insertUser("early-member");
        Long lateMember = insertUser("late-member");
        Long leftMember = insertUser("left-member");
        Long removedMember = insertUser("removed-member");
        Long recruitmentId = jdbc.queryForObject("""
                INSERT INTO recruitments (
                    author_id, title, category, status, visibility, activity_at, deadline_at,
                    now_participants, max_participants, created_at, updated_at
                ) VALUES (?, 'participants-test', 'MEAL', 'OPEN', 'VISIBLE', ?, ?, 3, 5, ?, ?) RETURNING id
                """, Long.class, author, now.plusDays(2), now.plusDays(1), now, now);
        insertParticipant(recruitmentId, author, "HOST", "JOINED", now.minusHours(2));
        insertParticipant(recruitmentId, lateMember, "MEMBER", "JOINED", now.minusHours(1));
        insertParticipant(recruitmentId, earlyMember, "MEMBER", "JOINED", now.minusHours(2));
        insertParticipant(recruitmentId, leftMember, "MEMBER", "LEFT", now.minusDays(1));
        insertParticipant(recruitmentId, removedMember, "MEMBER", "REMOVED", now.minusDays(1));

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var rows = new TransactionTemplate(transactionManager).execute(tx ->
                    repository.findParticipantRows(recruitmentId, RecruitmentParticipantStatus.JOINED));

            assertEquals(List.of(author, earlyMember, lateMember), rows.stream().map(row -> row.getUserId()).toList());
            assertEquals(RecruitmentParticipantRole.HOST, rows.getFirst().getRole());
            assertEquals("early-member", rows.get(1).getNickname());
            assertEquals("https://example.com/early-member.png", rows.get(1).getProfileImageUrl());
            assertEquals(now.minusHours(2), rows.get(1).getJoinedAt());
            assertEquals(1, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private Long insertUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (oauth_id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, name, name, "https://example.com/" + name + ".png", now, now);
    }

    private void insertParticipant(Long recruitmentId, Long userId, String role, String status, LocalDateTime joinedAt) {
        jdbc.update("""
                INSERT INTO recruitment_participants (
                    recruitment_id, user_id, role, status, joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, recruitmentId, userId, role, status, joinedAt, joinedAt, joinedAt);
    }
}
