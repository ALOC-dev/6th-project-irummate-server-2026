package com.irummate.domain.chat.repository;

import com.irummate.domain.chat.dto.ChatRoomListItemDto;
import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.entity.ChatRoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM ChatRoom room WHERE room.id = :roomId")
    Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM ChatRoom room WHERE room.recruitment.id = :recruitmentId")
    Optional<ChatRoom> findByRecruitmentIdForUpdate(@Param("recruitmentId") Long recruitmentId);

    @Query("SELECT room.type FROM ChatRoom room WHERE room.id = :roomId")
    Optional<ChatRoomType> findTypeById(@Param("roomId") Long roomId);

    long countByStatus(com.irummate.domain.chat.entity.ChatRoomStatus status);

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN cr.matchRequest mr
            WHERE mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRooms();

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN cr.matchRequest mr
            WHERE cr.status = :status
              AND mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomsByStatus(@Param("status") com.irummate.domain.chat.entity.ChatRoomStatus status);

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN cr.matchRequest mr
            WHERE cr.status = :status
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            """)
    long countByParticipantIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") com.irummate.domain.chat.entity.ChatRoomStatus status
    );

    // 매칭 요청 하나에 채팅방이 하나만 생성되도록 확인할 때 사용한다.
    Optional<ChatRoom> findByMatchRequest_Id(Long matchRequestId);

    // 현재 멤버의 방과 유형별 목록 정보를 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomListItemDto(
                cr.id,
                cr.type,
                CASE
                    WHEN lowUser.id = :userId THEN highUser.id
                    ELSE lowUser.id
                END,
                CASE
                    WHEN lowUser.id = :userId THEN highUser.nickname
                    ELSE lowUser.nickname
                END,
                CASE
                    WHEN lowUser.id = :userId THEN highUser.profileImageUrl
                    ELSE lowUser.profileImageUrl
                END,
                cr.status,
                CASE
                    WHEN lowUser.id = :userId THEN mr.userLowStatus
                    ELSE mr.userHighStatus
                END,
                CASE
                    WHEN lowUser.id = :userId THEN mr.userHighStatus
                    ELSE mr.userLowStatus
                END,
                recruitment.id,
                recruitment.title,
                recruitment.category,
                recruitment.nowParticipants
            )
            FROM ChatRoomMember membership
            JOIN membership.room cr
            LEFT JOIN cr.matchRequest mr
            LEFT JOIN mr.userLow lowUser
            LEFT JOIN mr.userHigh highUser
            LEFT JOIN cr.recruitment recruitment
            WHERE membership.user.id = :userId
              AND membership.leftAt IS NULL
            ORDER BY cr.createdAt DESC
            """)
    List<ChatRoomListItemDto> findRoomListItemsByUserId(@Param("userId") Long userId);

    // 매칭 처리와 계정 탈퇴에서 닫을 MATCH 방만 조회한다.
    @Query("""
            SELECT cr
            FROM ChatRoom cr
            JOIN cr.matchRequest mr
            WHERE cr.type = com.irummate.domain.chat.entity.ChatRoomType.MATCH
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            ORDER BY cr.createdAt DESC
            """)
    List<ChatRoom> findMatchRoomsByParticipantId(@Param("userId") Long userId);

    // 현재 유저의 상대방 userId를 조회한다.
    @Query("""
            SELECT CASE
                WHEN mr.userLow.id = :userId THEN mr.userHigh.id
                ELSE mr.userLow.id
            END
            FROM ChatRoom cr
            JOIN cr.matchRequest mr
            WHERE cr.id = :roomId
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            """)
    Optional<Long> findPartnerIdByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
