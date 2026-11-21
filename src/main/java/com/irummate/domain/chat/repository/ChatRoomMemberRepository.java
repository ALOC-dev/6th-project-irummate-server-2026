package com.irummate.domain.chat.repository;

import com.irummate.domain.chat.dto.ChatMemberReadCursor;
import com.irummate.domain.chat.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    boolean existsByRoom_IdAndUser_IdAndLeftAtIsNull(Long roomId, Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ChatRoomMember membership SET membership.leftAt = :leftAt
            WHERE membership.room.id = :roomId AND membership.user.id = :userId AND membership.leftAt IS NULL
            """)
    int leaveRoom(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("leftAt") LocalDateTime leftAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ChatRoomMember membership
            SET membership.lastReadMessageId = :messageId
            WHERE membership.room.id = :roomId AND membership.user.id = :userId
              AND membership.leftAt IS NULL
              AND (membership.lastReadMessageId IS NULL OR membership.lastReadMessageId < :messageId)
            """)
    int advanceReadCursor(@Param("roomId") Long roomId, @Param("userId") Long userId,
                          @Param("messageId") long messageId);

    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatMemberReadCursor(membership.user.id, membership.lastReadMessageId)
            FROM ChatRoomMember membership
            WHERE membership.room.id = :roomId AND membership.leftAt IS NULL
              AND membership.lastReadMessageId IS NOT NULL
            """)
    List<ChatMemberReadCursor> findReadCursorsByRoomId(@Param("roomId") Long roomId);
}
