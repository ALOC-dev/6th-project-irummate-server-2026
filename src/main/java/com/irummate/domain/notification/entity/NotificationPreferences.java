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
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "notification_preferences_user_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean paused;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean chat = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean matching = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean mate = true;

    @Column(name = "dorm_notice", nullable = false)
    @ColumnDefault("true")
    private boolean dormNotice = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean certification = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean recruitment = true;

    @Column(name = "admin_notice", nullable = false)
    @ColumnDefault("true")
    private boolean adminNotice = true;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant updatedAt;

    private NotificationPreferences(Users user, Instant now) {
        this.user = Objects.requireNonNull(user);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public static NotificationPreferences defaultsFor(Users user, Instant now) {
        return new NotificationPreferences(user, now);
    }

    public void update(boolean paused, boolean chat, boolean matching, boolean mate, boolean dormNotice, Instant now) {
        this.updatedAt = Objects.requireNonNull(now);
        this.paused = paused;
        this.chat = chat;
        this.matching = matching;
        this.mate = mate;
        this.dormNotice = dormNotice;
    }
}
