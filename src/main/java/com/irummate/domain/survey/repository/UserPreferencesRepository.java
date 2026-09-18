package com.irummate.domain.survey.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.irummate.domain.survey.entity.UserPreferences;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

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
                (50 + (GREATEST(0, 1 - ((up.lifestyle_vector <-> CAST(:vector AS vector)) / 3.0)) * 50))::numeric,
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
            @Param("limit") int limit
    );


    @Query(value = """
        SELECT
            up.user_id AS userId,
            ROUND(
                (50 + (GREATEST(0, 1 - ((up.lifestyle_vector <-> CAST(:vector AS vector)) / 3.0)) * 50))::numeric,
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
          AND up.user_id NOT IN (:excludedUserIds)
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
            @Param("excludedUserIds") List<Long> excludedUserIds,
            @Param("vector") String vector,   // ← String으로
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
    @Query("""
    SELECT up
    FROM UserPreferences up
    WHERE up.userId = :userId
    """)
    Optional<UserPreferences> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}
