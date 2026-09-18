package com.irummate.domain.chat.entity;

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

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMessage(Long roomId, Long senderId, String message) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.message = message;
        this.isRead = false;
    }

    public static ChatMessage create(Long roomId, Long senderId, String message) {
        return new ChatMessage(roomId, senderId, message);
    }

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void markAsRead()
    {
        this.isRead = true;
    }
}