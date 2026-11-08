package com.irummate.domain.chat.repository;

import com.irummate.domain.chat.dto.ChatRoomLastMessageDto;
import com.irummate.domain.chat.dto.ChatRoomUnreadCountDto;
import com.irummate.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 채팅방 목록에서 마지막 메시지를 보여주기 위해 사용한다.
    Optional<ChatMessage> findTopByRoom_IdOrderByCreatedAtDesc(Long roomId);

    // 특정 채팅방에서 내가 읽지 않은 메시지 수를 조회한다.
    int countByRoom_IdAndSender_IdNotAndIsReadFalse(Long roomId, Long senderId);

    // 현재 유저가 참여한 채팅방 안에서만 전체 안 읽은 메시지 수를 조회한다.
    @Query("""
            SELECT COUNT(cm)
            FROM ChatMessage cm
            JOIN cm.room cr
            JOIN cr.matchRequest mr
            WHERE (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
              AND cm.sender.id <> :userId
              AND cm.isRead = false
            """)
    int countUnreadByParticipantId(@Param("userId") Long userId);

    // 현재 시점에 읽음 처리할 메시지 중 가장 큰 ID를 조회한다.
    @Query("""
            SELECT MAX(cm.id)
            FROM ChatMessage cm
            WHERE cm.room.id = :roomId
              AND cm.sender.id <> :userId
              AND cm.isRead = false
            """)
    Optional<Long> findLastUnreadMessageId(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId
    );

    // 조회한 경계 ID까지의 상대방 메시지를 한 번의 UPDATE로 읽음 처리한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatMessage cm
            SET cm.isRead = true
            WHERE cm.room.id = :roomId
              AND cm.sender.id <> :userId
              AND cm.isRead = false
              AND cm.id <= :lastReadMessageId
            """)
    int markUnreadMessagesAsRead(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );

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
            FROM ChatMessage cm
            WHERE cm.room.id IN :roomIds
              AND cm.sender.id <> :userId
              AND cm.isRead = false
            GROUP BY cm.room.id
            """)
    List<ChatRoomUnreadCountDto> countUnreadByRoomIds(
            @Param("roomIds") List<Long> roomIds,
            @Param("userId") Long userId
    );
}
