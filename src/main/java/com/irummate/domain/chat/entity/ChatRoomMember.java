package com.irummate.domain.chat.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_members_room_user",
                columnNames = {"room_id", "user_id"}
        ),
        indexes = @Index(name = "idx_chat_room_members_user_left_room", columnList = "user_id, left_at, room_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "room_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_room_members_room")
    )
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_room_members_user")
    )
    private Users user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    private ChatRoomMember(ChatRoom room, Users user, LocalDateTime joinedAt) {
        this.room = Objects.requireNonNull(room);
        this.user = Objects.requireNonNull(user);
        this.joinedAt = Objects.requireNonNull(joinedAt);
    }

    public static ChatRoomMember join(ChatRoom room, Users user, LocalDateTime joinedAt) {
        return join(room, user, joinedAt, null);
    }

    public static ChatRoomMember join(ChatRoom room, Users user, LocalDateTime joinedAt, Long lastReadMessageId) {
        ChatRoomMember membership = new ChatRoomMember(room, user, joinedAt);
        membership.lastReadMessageId = lastReadMessageId;
        return membership;
    }
}
