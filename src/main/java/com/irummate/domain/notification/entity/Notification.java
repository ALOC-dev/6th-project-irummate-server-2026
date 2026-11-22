package com.irummate.domain.notification.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "notifications", uniqueConstraints = {
        @UniqueConstraint(name = "notifications_recipient_id_event_key_key", columnNames = {"recipient_id", "event_key"}),
        @UniqueConstraint(name = "notifications_id_recipient_id_category_key", columnNames = {"id", "recipient_id", "category"})
}, indexes = {
        @Index(name = "idx_notifications_recipient_latest", columnList = "recipient_id, created_at DESC, id DESC"),
        @Index(name = "idx_notifications_recipient_category_latest", columnList = "recipient_id, category, created_at DESC, id DESC")
}, check = @CheckConstraint(name = "notifications_category_check",
        constraint = "category IN ('MATCHING', 'MATE', 'DORM_NOTICE', 'CERTIFICATION', 'RECRUITMENT', 'ADMIN_NOTICE')"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "notifications_recipient_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, updatable = false, length = 50)
    private String type;

    @Column(name = "event_key", nullable = false, updatable = false, length = 200)
    private String eventKey;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false, length = 128)
    private String targetId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "read_at", columnDefinition = "timestamp with time zone")
    private Instant readAt;

    private Notification(Users recipient, NotificationCategory category, String type, String eventKey,
                         String title, String body, String targetType, String targetId, Instant createdAt) {
        if (category == NotificationCategory.CHAT) {
            throw new IllegalArgumentException("채팅 푸시는 알림함에 저장할 수 없습니다.");
        }
        this.recipient = Objects.requireNonNull(recipient);
        this.category = Objects.requireNonNull(category);
        this.type = Objects.requireNonNull(type);
        this.eventKey = Objects.requireNonNull(eventKey);
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.targetType = Objects.requireNonNull(targetType);
        this.targetId = targetId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Notification create(Users recipient, NotificationCategory category, String type, String eventKey,
                                      String title, String body, String targetType, String targetId, Instant createdAt) {
        return new Notification(recipient, category, type, eventKey, title, body, targetType, targetId, createdAt);
    }

    public void markAsRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = Objects.requireNonNull(readAt);
        }
    }
}
