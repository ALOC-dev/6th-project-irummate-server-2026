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
            JOIN ChatRoom cr ON cr.id = cm.roomId
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomMessages();

    @Query("""
            SELECT COUNT(cm)
            FROM ChatMessage cm
            JOIN ChatRoom cr ON cr.id = cm.roomId
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE cm.createdAt >= :createdAt
              AND mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomMessagesByCreatedAtGreaterThanEqual(@Param("createdAt") java.time.LocalDateTime createdAt);

    // 특정 채팅방의 메시지를 최신순으로 조회한다.
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // cursor보다 오래된 메시지를 최신순으로 조회한다.
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Pageable pageable);

    // 채팅방 목록에서 마지막 메시지를 보여주기 위해 사용한다.
    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);

    // 특정 채팅방에서 내가 읽지 않은 메시지 수를 조회한다.
    int countByRoomIdAndSenderIdNotAndIsReadFalse(Long roomId, Long senderId);

    // 현재 유저가 참여한 채팅방 안에서만 전체 안 읽은 메시지 수를 조회한다.
    @Query("""
            SELECT COUNT(cm)
            FROM ChatMessage cm
            JOIN ChatRoom cr ON cr.id = cm.roomId
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
              AND cm.senderId <> :userId
              AND cm.isRead = false
            """)
    int countUnreadByParticipantId(@Param("userId") Long userId);

    // 읽음 처리 대상 메시지를 조회한다.
    List<ChatMessage> findByRoomIdAndSenderIdNotAndIsReadFalse(Long roomId, Long senderId);

    // 채팅방 목록에 표시할 각 방의 마지막 메시지를 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomLastMessageDto(
                cm.roomId,
                cm.message,
                cm.createdAt
            )
            FROM ChatMessage cm
            WHERE cm.id IN (
                SELECT MAX(cm2.id)
                FROM ChatMessage cm2
                WHERE cm2.roomId IN :roomIds
                GROUP BY cm2.roomId
            )
            """)
    List<ChatRoomLastMessageDto> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);

    // 채팅방 목록에 표시할 방별 안 읽은 메시지 개수를 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomUnreadCountDto(
                cm.roomId,
                COUNT(cm)
            )
            FROM ChatMessage cm
            WHERE cm.roomId IN :roomIds
              AND cm.senderId <> :userId
              AND cm.isRead = false
            GROUP BY cm.roomId
            """)
    List<ChatRoomUnreadCountDto> countUnreadByRoomIds(
            @Param("roomIds") List<Long> roomIds,
            @Param("userId") Long userId
    );
}
