package com.irummate.domain.chat.entity;

import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.recruitment.entity.Recruitments;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_rooms_match_request", columnNames = "match_request_id"),
                @UniqueConstraint(name = "uk_chat_rooms_recruitment", columnNames = "recruitment_id")
        },
        check = @CheckConstraint(
                name = "ck_chat_rooms_type",
                constraint = """
                        (type = 'MATCH' AND match_request_id IS NOT NULL AND recruitment_id IS NULL AND close_at IS NULL)
                        OR (type = 'RECRUITMENT' AND match_request_id IS NULL AND recruitment_id IS NOT NULL AND close_at IS NOT NULL)
                        """
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "match_request_id",
            foreignKey = @ForeignKey(name = "fk_chat_rooms_match_request")
    )
    private MatchRequests matchRequest;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "recruitment_id",
            foreignKey = @ForeignKey(name = "fk_chat_rooms_recruitment")
    )
    private Recruitments recruitment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ChatRoomType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChatRoomStatus status = ChatRoomStatus.OPEN;

    @Column(name = "close_at")
    private LocalDateTime closeAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatRoom(ChatRoomType type, MatchRequests matchRequest, Recruitments recruitment, LocalDateTime closeAt) {
        this.type = type;
        this.matchRequest = matchRequest;
        this.recruitment = recruitment;
        this.closeAt = closeAt;
    }

    public static ChatRoom forMatch(MatchRequests matchRequest) {
        return new ChatRoom(ChatRoomType.MATCH, Objects.requireNonNull(matchRequest), null, null);
    }

    public static ChatRoom forRecruitment(Recruitments recruitment) {
        Objects.requireNonNull(recruitment);
        return new ChatRoom(ChatRoomType.RECRUITMENT, null, recruitment, recruitment.getActivityAt().plusHours(72));
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public boolean isClosed() {
        return this.status == ChatRoomStatus.CLOSED;
    }

    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }

    public void rescheduleRecruitment(LocalDateTime activityAt) {
        this.closeAt = activityAt.plusHours(72);
    }
}
