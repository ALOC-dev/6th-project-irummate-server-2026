package com.irummate.domain.block.repository;

import com.irummate.domain.block.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// 차단 관계의 저장·삭제 및 조회 쿼리
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    interface BlockedUserView {
        Long getUserId();
        String getNickname();
        LocalDateTime getCreatedAt();
    }

    @Modifying
    @Query(value = """
            INSERT INTO user_blocks (blocker_id, blocked_id, created_at)
            VALUES (:blockerId, :blockedId, :createdAt)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId,
                       @Param("createdAt") LocalDateTime createdAt);

    // 본인 → 상대방의 단방향 차단 상태 확인에 사용한다.
    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM user_blocks
                WHERE (blocker_id = :userA AND blocked_id = :userB)
                   OR (blocker_id = :userB AND blocked_id = :userA)
            )
            """, nativeQuery = true)
    // 공통 양방향 검증용으로 전체 목록 대신 존재 여부만 조회한다.
    boolean existsBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    // 해제 시 지정된 실행자 → 대상 관계만 삭제한다.
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query(value = """
            SELECT b.blocked.id AS userId, b.blocked.nickname AS nickname, b.createdAt AS createdAt
            FROM UserBlock b WHERE b.blocker.id = :blockerId
            ORDER BY b.createdAt DESC, b.id DESC
            """, countQuery = "SELECT COUNT(b) FROM UserBlock b WHERE b.blocker.id = :blockerId")
    // 내 차단 목록을 최신순으로 조회하고 전체 건수를 계산한다.
    Page<BlockedUserView> findBlockedUsers(@Param("blockerId") Long blockerId, Pageable pageable);
}
