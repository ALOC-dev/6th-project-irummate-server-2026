package com.irummate.domain.survey.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.irummate.domain.survey.entity.UserPreferences;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    // 주어진 사용자 중 한 명이라도 최종 확정(is_matched=true)됐는지 확인
    // 엔티티를 만들지 않고 PK로 존재 여부만 확인
    boolean existsByUserIdInAndIsMatchedTrue(List<Long> userIds);

    interface RecommendationCandidate {
        Long getUserId();
        Double getMatchPercentage();
    }



    Optional<UserPreferences> findByUserId(Long userId);

    @Query("""
        SELECT up
        FROM UserPreferences up
        JOIN FETCH up.user u
        JOIN FETCH u.userDetails
        WHERE up.userId = :userId
    """)
    Optional<UserPreferences> findByUserIdWithUserDetails(@Param("userId") Long userId);


    @Query(value = """
        SELECT
            up.user_id AS userId,
            ROUND(
                (50 + (GREATEST(0, 1 - ((up.lifestyle_vector <-> CAST(:vector AS vector)) / 3.0)) * 40))::numeric,
                1
            )::double precision AS matchPercentage
        FROM user_preferences up
        JOIN users u ON up.user_id = u.id
        JOIN user_details ud ON up.user_id = ud.user_id
        WHERE u.status = 'ACTIVE'
          AND u.role = 'USER'
          AND up.is_completed = true
          AND up.is_matched = false
          AND up.user_id != :myUserId
          AND ud.gender = :gender
          AND up.smoking_status = :smokingStatus
          AND EXISTS (
              SELECT 1 FROM certifications c
              WHERE c.user_id = up.user_id
                AND c.semester = :semester
                AND c.certification_status = 'APPROVED'
                AND (c.expires_at IS NULL OR c.expires_at > :now)
          )
          AND NOT EXISTS (
              SELECT 1
              FROM match_requests mr
              WHERE mr.user_low_id = LEAST(:myUserId, up.user_id)
                AND mr.user_high_id = GREATEST(:myUserId, up.user_id)
          )
        ORDER BY up.lifestyle_vector <-> CAST(:vector AS vector)
        LIMIT :limit
    """, nativeQuery = true)
    List<RecommendationCandidate> findNewRecommendationCandidates(
            @Param("myUserId") Long myUserId,
            @Param("gender") String gender,
            @Param("smokingStatus") Integer smokingStatus,
            @Param("vector") String vector,   // ← PGvector에서 String으로
            @Param("semester") String semester,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    // 흡연 필터링 완화한 후보자 탐색
    @Query(value = """
       SELECT
           up.user_id AS userId,
           ROUND(
               (50 + (GREATEST(
                   0,
                   1 - ((up.lifestyle_vector <-> CAST(:vector AS vector)) / 3.0)
               ) * 40))::numeric,
               1
           )::double precision AS matchPercentage
       FROM user_preferences up
       JOIN users u ON up.user_id = u.id
       JOIN user_details ud ON up.user_id = ud.user_id
       WHERE u.status = 'ACTIVE'
         AND u.role = 'USER'
         AND up.is_completed = true
         AND up.is_matched = false
         AND up.user_id != :myUserId
         AND ud.gender = :gender
         AND EXISTS (
             SELECT 1 FROM certifications c
             WHERE c.user_id = up.user_id
               AND c.semester = :semester
               AND c.certification_status = 'APPROVED'
               AND (c.expires_at IS NULL OR c.expires_at > :now)
         )
         AND NOT EXISTS (
             SELECT 1
             FROM match_requests mr
             WHERE mr.user_low_id = LEAST(:myUserId, up.user_id)
               AND mr.user_high_id = GREATEST(:myUserId, up.user_id)
         )
       ORDER BY up.lifestyle_vector <-> CAST(:vector AS vector)
       LIMIT :limit
       """, nativeQuery = true)
    List<RecommendationCandidate> findNewRecommendationCandidatesIgnoringSmoking(
            @Param("myUserId") Long myUserId,
            @Param("gender") String gender,
            @Param("vector") String vector,
            @Param("semester") String semester,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT up
    FROM UserPreferences up
    WHERE up.userId IN :ids
    ORDER BY up.userId ASC
    """)
    List<UserPreferences> findAllByIdsForUpdate(
            @Param("ids") List<Long> ids
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "0"
            )
    )
    @Query("""
    SELECT up
    FROM UserPreferences up
    WHERE up.userId = :userId
    """)
    Optional<UserPreferences> findByUserIdForUpdate(
            @Param("userId") Long userId
    );

    // findByUserIdForUpdate와 같은 행 잠금이지만 NOWAIT 없이 대기
    // (트랜잭션의 lock_timeout까지 기다림)
    // 관리자 제재처럼 사용자의 match()가 잠깐 잡고 있어도 끝날 때까지 기다려야 하는 경우에 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT up
    FROM UserPreferences up
    WHERE up.userId = :userId
    """)
    Optional<UserPreferences> findByUserIdForUpdateWaiting(
            @Param("userId") Long userId
    );
}
