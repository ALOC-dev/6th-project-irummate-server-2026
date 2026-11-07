package com.irummate.domain.chat.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Users sender;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMessage(ChatRoom room, Users sender, String message) {
        this.room = room;
        this.sender = sender;
        this.message = message;
        this.isRead = false;
    }

    public static ChatMessage create(ChatRoom room, Users sender, String message) {
        return new ChatMessage(room, sender, message);
    }

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

}
