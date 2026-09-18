package com.irummate.domain.chat.entity;

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

    // 하나의 매칭 요청에서 채팅방이 중복 생성되지 않도록 unique 제약을 둔다.
    //MatchRequest 엔티티 생기면 수정하자.
    @Column(name = "match_request_id", nullable = false, unique = true)
    private Long matchRequestId;

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