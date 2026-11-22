package com.irummate.domain.notification.entity;

import com.irummate.domain.auth.entity.LoginSession;
import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_jobs",
        uniqueConstraints = {
                @UniqueConstraint(name = "push_jobs_installation_id_recipient_id_event_key_key",
                        columnNames = {"installation_id", "recipient_id", "event_key"}),
                @UniqueConstraint(name = "uk_push_jobs_topic_event", columnNames = {"topic", "event_key"})
        },
        indexes = @Index(name = "idx_push_jobs_recipient", columnList = "recipient_id"),
        check = {
                @CheckConstraint(name = "chk_push_job_inbox_link",
                        constraint = "topic IS NOT NULL OR (category = 'CHAT') = (notification_id IS NULL)"),
                @CheckConstraint(name = "chk_push_job_target", constraint = """
                        (topic IS NULL AND installation_id IS NOT NULL AND recipient_id IS NOT NULL
                            AND login_session_id IS NOT NULL)
                        OR (topic IS NOT NULL AND installation_id IS NULL AND recipient_id IS NULL
                            AND login_session_id IS NULL AND notification_id IS NULL AND category = 'ADMIN_NOTICE')
                        """),
                @CheckConstraint(name = "chk_push_job_expiry", constraint = "expires_at > created_at"),
                @CheckConstraint(name = "push_jobs_attempt_count_check", constraint = "attempt_count >= 0"),
                @CheckConstraint(name = "push_jobs_payload_check", constraint = "jsonb_typeof(payload) = 'object'"),
                @CheckConstraint(name = "chk_push_job_lease", constraint = """
                        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
                        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_expires_at IS NULL)
                        """),
                @CheckConstraint(name = "chk_push_job_finished",
                        constraint = "(status IN ('SENT', 'FAILED', 'SKIPPED')) = (finished_at IS NOT NULL)")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installation_id", updatable = false,
            foreignKey = @ForeignKey(name = "push_jobs_installation_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PushDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", updatable = false,
            foreignKey = @ForeignKey(name = "push_jobs_recipient_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users recipient;

    // 작업 생성 당시의 세션 ID를 보관한다. 설치의 현재 세션은 이후 바뀔 수 있다.
    @Column(name = "login_session_id", updatable = false)
    private UUID loginSessionId;

    @Column(updatable = false, length = 128)
    private String topic;

    // DDL에서 notification_id, recipient_id, category를 묶어 복합 FK로 검증한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationCategory category;

    @Column(name = "event_key", nullable = false, updatable = false, length = 200)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Payload payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @ColumnDefault("'PENDING'")
    private PushJobStatus status = PushJobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @ColumnDefault("0")
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant nextAttemptAt;

    @Column(name = "expires_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_expires_at", columnDefinition = "timestamp with time zone")
    private Instant leaseExpiresAt;

    @Column(name = "finished_at", columnDefinition = "timestamp with time zone")
    private Instant finishedAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    private PushJob(PushDevice device, Notification notification, NotificationCategory category, String eventKey,
                    Payload payload, Instant now, Instant expiresAt) {
        LoginSession session = device.getLoginSession();
        Users recipient = session.getUser();
        if (notification != null && !recipient.getId().equals(notification.getRecipient().getId())) {
            throw new IllegalArgumentException("알림 수신자와 기기의 로그인 사용자가 일치해야 합니다.");
        }
        if (!Objects.requireNonNull(expiresAt).isAfter(Objects.requireNonNull(now))) {
            throw new IllegalArgumentException("푸시 작업 만료 시각은 생성 시각보다 늦어야 합니다.");
        }
        this.device = device;
        this.recipient = recipient;
        this.loginSessionId = Objects.requireNonNull(session.getId());
        this.notification = notification;
        this.category = category;
        this.eventKey = Objects.requireNonNull(eventKey);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = now;
        this.nextAttemptAt = now;
        this.expiresAt = expiresAt;
    }

    public static PushJob forNotification(PushDevice device, Notification notification, Payload payload,
                                          Instant now, Instant expiresAt) {
        return new PushJob(device, notification, notification.getCategory(), notification.getEventKey(),
                payload, now, expiresAt);
    }

    public static PushJob forChatMessage(PushDevice device, String eventKey, Payload payload,
                                         Instant now, Instant expiresAt) {
        return new PushJob(device, null, NotificationCategory.CHAT, eventKey, payload, now, expiresAt);
    }

    public record Payload(String title, String body, Map<String, String> data) {
        public Payload {
            Objects.requireNonNull(title);
            Objects.requireNonNull(body);
            data = Map.copyOf(data);
        }
    }
}
