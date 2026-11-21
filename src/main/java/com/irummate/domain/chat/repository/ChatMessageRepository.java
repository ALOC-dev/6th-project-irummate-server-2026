package com.irummate.domain.chat.repository;

import com.irummate.domain.chat.dto.ChatRoomLastMessageDto;
import com.irummate.domain.chat.dto.ChatRoomUnreadCountDto;
import com.irummate.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime createdAt);

    @Query("""
            SELECT COUNT(cm)
            FROM ChatMessage cm
            JOIN cm.room cr
            JOIN cr.matchRequest mr
            WHERE mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomMessages();

    @Query("""
            SELECT COUNT(cm)
            FROM ChatMessage cm
            JOIN cm.room cr
            JOIN cr.matchRequest mr
            WHERE cm.createdAt >= :createdAt
              AND mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomMessagesByCreatedAtGreaterThanEqual(@Param("createdAt") java.time.LocalDateTime createdAt);

    // 특정 채팅방의 메시지를 최신순으로 조회한다.
    List<ChatMessage> findByRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    // cursor보다 오래된 메시지를 최신순으로 조회한다.
    List<ChatMessage> findByRoom_IdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Pageable pageable);

    boolean existsByIdAndRoom_Id(Long messageId, Long roomId);

    @Query("SELECT MAX(message.id) FROM ChatMessage message WHERE message.room.id = :roomId")
    Optional<Long> findLastMessageId(@Param("roomId") Long roomId);

    @Query("""
            SELECT membership.user.id AS userId, COUNT(message.id) AS totalUnreadCount,
                   SUM(CASE WHEN membership.room.id = :roomId THEN 1 ELSE 0 END) AS unreadCount
            FROM ChatRoomMember membership
            JOIN ChatMessage message ON message.room = membership.room
            WHERE membership.user.id IN :userIds AND membership.leftAt IS NULL
              AND message.sender.id <> membership.user.id
              AND message.id > COALESCE(membership.lastReadMessageId, 0L)
            GROUP BY membership.user.id
            """)
    List<RecipientUnreadCount> countUnreadByRecipients(@Param("roomId") Long roomId,
                                                        @Param("userIds") List<Long> userIds);

    interface RecipientUnreadCount {
        Long getUserId();
        Long getTotalUnreadCount();
        Long getUnreadCount();
    }

    // 현재 유저가 참여한 채팅방 안에서만 전체 안 읽은 메시지 수를 조회한다.
    @Query("""
            SELECT COUNT(cm)
            FROM ChatRoomMember membership
            JOIN ChatMessage cm ON cm.room = membership.room
            WHERE membership.user.id = :userId AND membership.leftAt IS NULL
              AND cm.sender.id <> :userId
              AND cm.id > COALESCE(membership.lastReadMessageId, 0L)
            """)
    int countUnreadByCurrentMemberId(@Param("userId") Long userId);

    // 채팅방 목록에 표시할 각 방의 마지막 메시지를 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomLastMessageDto(
                cm.room.id,
                cm.message,
                cm.createdAt
            )
            FROM ChatMessage cm
            WHERE cm.id IN (
                SELECT MAX(cm2.id)
                FROM ChatMessage cm2
                WHERE cm2.room.id IN :roomIds
                GROUP BY cm2.room.id
            )
            """)
    List<ChatRoomLastMessageDto> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);

    // 채팅방 목록에 표시할 방별 안 읽은 메시지 개수를 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomUnreadCountDto(
                cm.room.id,
                COUNT(cm)
            )
            FROM ChatRoomMember membership
            JOIN ChatMessage cm ON cm.room = membership.room
            WHERE membership.user.id = :userId AND membership.leftAt IS NULL
              AND membership.room.id IN :roomIds
              AND cm.sender.id <> :userId
              AND cm.id > COALESCE(membership.lastReadMessageId, 0L)
            GROUP BY cm.room.id
            """)
    List<ChatRoomUnreadCountDto> countUnreadByRoomIds(
            @Param("roomIds") List<Long> roomIds,
            @Param("userId") Long userId
    );
}
