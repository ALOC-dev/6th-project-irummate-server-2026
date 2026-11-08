package com.irummate.domain.chat.entity;

import com.irummate.domain.matching.entity.MatchRequests;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "match_request_id",
            nullable = false,
            unique = true
    )
    private MatchRequests matchRequest;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRoomStatus status = ChatRoomStatus.OPEN;


    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public boolean isClosed()
    {
        return this.status == ChatRoomStatus.CLOSED;
    }
    public void close()
    {
        this.status = ChatRoomStatus.CLOSED;
    }
}