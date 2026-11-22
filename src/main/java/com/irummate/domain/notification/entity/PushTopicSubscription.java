package com.irummate.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_topic_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushTopicSubscription {
    @Id
    @Column(name = "fcm_token", columnDefinition = "text")
    private String fcmToken;

    // null은 마지막 FCM 호출의 적용 여부를 다시 확인해야 한다는 뜻이다.
    private Boolean subscribed;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant nextAttemptAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_expires_at", columnDefinition = "timestamp with time zone")
    private Instant leaseExpiresAt;
}
