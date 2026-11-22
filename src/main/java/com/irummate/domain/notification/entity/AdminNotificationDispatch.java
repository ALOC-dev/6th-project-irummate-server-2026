package com.irummate.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "admin_notification_dispatches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminNotificationDispatch {
    @Id
    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;
}
