package com.irummate.domain.matching.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.irummate.domain.matching.entity.MatchRequests;

@Repository
public interface MatchRepository extends JpaRepository<MatchRequests, Long> {


    // 후보자를 추천하는 쿼리
    // MatchRequest의 상태를 recommended로 변경
    @Modifying
    @Query(value = """
    UPDATE match_requests mr
    SET
        user_low_status = CASE
           WHEN mr.user_low_id = :userId
           THEN 'RECOMMENDED'
           ELSE mr.user_low_status
       END,
       user_high_status = CASE
           WHEN mr.user_high_id = :userId
           THEN 'RECOMMENDED'
           ELSE mr.user_high_status
       END,
       user_low_recommended_at = CASE
           WHEN mr.user_low_id = :userId
           THEN :recommendedAt
           ELSE mr.user_low_recommended_at
       END,
       user_high_recommended_at = CASE
           WHEN mr.user_high_id = :userId
           THEN :recommendedAt
           ELSE mr.user_high_recommended_at
       END
    WHERE mr.match_request_id = :matchRequestId
        AND (
            (
                mr.user_low_id = :userId
                AND mr.user_low_status = 'NONE'
                AND mr.user_high_status IN ('NONE', 'RECOMMENDED')
                AND EXISTS (
                    SELECT 1
                    FROM users u
                    JOIN user_preferences up ON up.user_id = u.id
                    WHERE u.id = mr.user_high_id
                        AND u.status = 'ACTIVE'
                        AND u.role = 'USER'
                        AND up.is_completed = true
                        AND up.is_matched = false
                )
        )
        OR
        (
            mr.user_high_id = :userId
            AND mr.user_high_status = 'NONE'
            AND mr.user_low_status IN ('NONE', 'RECOMMENDED')
            AND EXISTS (
                SELECT 1
                FROM users u
                JOIN user_preferences up ON up.user_id = u.id
                WHERE u.id = mr.user_low_id
                    AND u.status = 'ACTIVE'
                    AND u.role = 'USER'
                    AND up.is_completed = true
                    AND up.is_matched = false
            )
        )
    )
    """, nativeQuery = true)
    int recommendCandidateIfAvailable(
            @Param("matchRequestId") Long matchRequestId,
            @Param("userId") Long userId,
            @Param("recommendedAt") LocalDateTime recommendedAt
    );




    // 후보자를 저장하는 쿼리
    // 중복 여부를 확인하며 넣고 실패 시 0을 성공 시 1을 반환
    // 이 쿼리를 통해 한명이 실패해도 예외를 발생시키지 않고
    // 다음 후보자로 넘어갈 수 있음
    @Modifying
    @Query(value = """
    INSERT INTO match_requests (
        user_low_id,
        user_high_id,
        user_low_preferences_id,
        user_high_preferences_id,
        match_percentage,
        user_low_status,
        user_high_status,
        created_at
    )
    VALUES (
        :userLowId,
        :userHighId,
        :userLowPreferencesId,
        :userHighPreferencesId,
        :matchPercentage,
        'NONE',
        'NONE',
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (user_low_id, user_high_id)
    DO NOTHING
    """, nativeQuery = true)
    int insertCandidateIfAbsent(
            @Param("userLowId") Long userLowId,
            @Param("userHighId") Long userHighId,
            @Param("userLowPreferencesId") Long userLowPreferencesId,
            @Param("userHighPreferencesId") Long userHighPreferencesId,
            @Param("matchPercentage") Double matchPercentage
    );


    // match 알고리즘 실행 이전
    // 사용자에게 추천되지 않은 후보자들을 검색
    // 매칭 점수가 높은 순대로 검색
    @Query("""
        SELECT mr
        FROM MatchRequests mr
        WHERE (
            mr.userLow.id = :userId
            AND mr.userLowStatus = MatchStatus.NONE
            AND mr.userHighStatus IN (
                MatchStatus.NONE,
                MatchStatus.RECOMMENDED
            )
            AND mr.userHigh.status = UserStatus.ACTIVE
            AND mr.userHigh.role = UserRole.USER
            AND mr.userHighPreferences.isCompleted = true
            AND mr.userHighPreferences.isMatched = false
        )
        OR (
            mr.userHigh.id = :userId
            AND mr.userHighStatus = MatchStatus.NONE
            AND mr.userLowStatus IN (
                MatchStatus.NONE,
                MatchStatus.RECOMMENDED
            )
            AND mr.userLow.status = UserStatus.ACTIVE
            AND mr.userLow.role = UserRole.USER
            AND mr.userLowPreferences.isCompleted = true
            AND mr.userLowPreferences.isMatched = false
        )
        ORDER BY mr.matchPercentage DESC
    """)
    List<MatchRequests> findAvailableCandidates(
            Long userId,
            Pageable pageable
    );


    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN mr.user_low_status = 'HEART' THEN 1 ELSE 0 END), 0)
                 + COALESCE(SUM(CASE WHEN mr.user_high_status = 'HEART' THEN 1 ELSE 0 END), 0)
            FROM match_requests mr
            JOIN users ul ON ul.id = mr.user_low_id
            JOIN users uh ON uh.id = mr.user_high_id
            WHERE ul.role = 'USER'
              AND ul.status = 'ACTIVE'
              AND uh.role = 'USER'
              AND uh.status = 'ACTIVE'
            """, nativeQuery = true)
    long countHeartSent();

    @Query(value = """
            SELECT COUNT(*)
            FROM match_requests mr
            JOIN users ul ON ul.id = mr.user_low_id
            JOIN users uh ON uh.id = mr.user_high_id
            WHERE mr.user_low_status = 'HEART'
              AND mr.user_high_status = 'HEART'
              AND ul.role = 'USER'
              AND ul.status = 'ACTIVE'
              AND uh.role = 'USER'
              AND uh.status = 'ACTIVE'
            """, nativeQuery = true)
    long countHeartMatched();

    @Query(value = """
            SELECT COUNT(*)
            FROM match_requests mr
            JOIN users ul ON ul.id = mr.user_low_id
            JOIN users uh ON uh.id = mr.user_high_id
            WHERE (
                    (mr.user_low_status = 'FINAL_CONFIRMED' AND mr.user_high_status = 'HEART')
                 OR (mr.user_high_status = 'FINAL_CONFIRMED' AND mr.user_low_status = 'HEART')
            )
              AND ul.role = 'USER'
              AND ul.status = 'ACTIVE'
              AND uh.role = 'USER'
              AND uh.status = 'ACTIVE'
            """, nativeQuery = true)
    long countConfirmPending();

    @Query(value = """
            SELECT COUNT(*)
            FROM match_requests mr
            JOIN users ul ON ul.id = mr.user_low_id
            JOIN users uh ON uh.id = mr.user_high_id
            WHERE mr.user_low_status = 'FINAL_CONFIRMED'
              AND mr.user_high_status = 'FINAL_CONFIRMED'
              AND ul.role = 'USER'
              AND ul.status = 'ACTIVE'
              AND uh.role = 'USER'
              AND uh.status = 'ACTIVE'
            """, nativeQuery = true)
    long countFinalConfirmed();

    @Query(value = """
            SELECT COUNT(*)
            FROM match_requests mr
            JOIN users ul ON ul.id = mr.user_low_id
            JOIN users uh ON uh.id = mr.user_high_id
            WHERE (mr.user_low_status = 'CLOSED' OR mr.user_high_status = 'CLOSED')
              AND ul.role = 'USER'
              AND ul.status = 'ACTIVE'
              AND uh.role = 'USER'
              AND uh.status = 'ACTIVE'
            """, nativeQuery = true)
    long countClosed();

    @Query("""
            SELECT COUNT(mr)
            FROM MatchRequests mr
            WHERE (mr.userLow.id = :userId AND mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.HEART)
               OR (mr.userHigh.id = :userId AND mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.HEART)
            """)
    long countHeartSentByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(mr)
            FROM MatchRequests mr
            WHERE (mr.userLow.id = :userId AND mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.HEART)
               OR (mr.userHigh.id = :userId AND mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.HEART)
            """)
    long countHeartReceivedByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(mr)
            FROM MatchRequests mr
            WHERE (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
              AND mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.HEART
              AND mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.HEART
            """)
    long countHeartMatchedByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(mr) > 0
            FROM MatchRequests mr
            WHERE (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
              AND mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.FINAL_CONFIRMED
              AND mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.FINAL_CONFIRMED
            """)
    boolean existsFinalConfirmedByUserId(@Param("userId") Long userId);




    // 자기 자신이 포함된 match를 조회
    // 단, 내가 아직 추천받지 않았거나(NONE) 내가 거절한(REJECTED) match는 조회 대상에서 제외
    // (상대방이 거절한 것은 포함)
    @Query("""
    SELECT DISTINCT mr
    FROM MatchRequests mr
    JOIN FETCH mr.userLow ul
    JOIN FETCH ul.userDetails
    JOIN FETCH ul.userPreferences
    JOIN FETCH mr.userHigh uh
    JOIN FETCH uh.userDetails
    JOIN FETCH uh.userPreferences
    WHERE (
        mr.userLow.id = :userId
        AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
        AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
        AND mr.userLowStatus <> com.irummate.domain.matching.entity.MatchStatus.CLOSED
        AND mr.userHighStatus <> com.irummate.domain.matching.entity.MatchStatus.CLOSED
        AND (
            mr.userLowStatus NOT IN (
                com.irummate.domain.matching.entity.MatchStatus.NONE,
                com.irummate.domain.matching.entity.MatchStatus.REJECTED
            )
            OR (
                mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.NONE
                AND mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.HEART
            )
        )
    )
    OR (
        mr.userHigh.id = :userId
        AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
        AND mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
        AND mr.userLowStatus <> com.irummate.domain.matching.entity.MatchStatus.CLOSED
        AND mr.userHighStatus <> com.irummate.domain.matching.entity.MatchStatus.CLOSED
        AND (
            mr.userHighStatus NOT IN (
                com.irummate.domain.matching.entity.MatchStatus.NONE,
                com.irummate.domain.matching.entity.MatchStatus.REJECTED
            )
            OR (
                mr.userHighStatus = com.irummate.domain.matching.entity.MatchStatus.NONE
                AND mr.userLowStatus = com.irummate.domain.matching.entity.MatchStatus.HEART
            )
        )
    )
    ORDER BY mr.matchPercentage DESC
    """)
    List<MatchRequests> findAllVisibleByUserId(@Param("userId") Long userId);


    @Query("""
    SELECT mr
    FROM MatchRequests mr
    WHERE mr.id <> :confirmedMatchRequestId
    AND (
        mr.userLow.id = :userId
        OR mr.userHigh.id = :userId
    )
    """)
    List<MatchRequests> findAllByUserIdExceptConfirmed(
            @Param("userId") Long userId,
            @Param("confirmedMatchRequestId") Long confirmedMatchRequestId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT mr
    FROM MatchRequests mr
    WHERE mr.userLow.id = LEAST(:userId1, :userId2)
    AND mr.userHigh.id = GREATEST(:userId1, :userId2)
    """)
    Optional<MatchRequests> findByIds(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("""
    SELECT mr
    FROM MatchRequests mr
    WHERE mr.userLow.id = LEAST(:userId1, :userId2)
    AND mr.userHigh.id = GREATEST(:userId1, :userId2)
    """)
    Optional<MatchRequests> findByIdsWithoutLock(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("""
    SELECT mr
    FROM MatchRequests mr
    WHERE mr.userLow.id = :userId
    OR mr.userHigh.id = :userId
    """)
    List<MatchRequests> findAllByUserId(@Param("userId") Long userId);
}
