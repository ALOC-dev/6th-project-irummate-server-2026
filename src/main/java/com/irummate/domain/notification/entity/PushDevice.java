package com.irummate.domain.notification.entity;

import com.irummate.domain.auth.entity.LoginSession;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_devices",
        uniqueConstraints = @UniqueConstraint(name = "push_devices_fcm_token_key", columnNames = "fcm_token"),
        indexes = @Index(name = "idx_push_devices_session", columnList = "login_session_id"),
        check = @CheckConstraint(name = "chk_push_device_active_token",
                constraint = "(disabled_at IS NULL) = (fcm_token IS NOT NULL)"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice {

    @Id
    @Column(name = "installation_id", updatable = false)
    private UUID installationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "login_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "push_devices_login_session_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LoginSession loginSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @Column(name = "fcm_token", columnDefinition = "text")
    private String fcmToken;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Column(name = "disabled_at", columnDefinition = "timestamp with time zone")
    private Instant disabledAt;

    private PushDevice(UUID installationId, LoginSession loginSession, PushPlatform platform, String fcmToken, Instant now) {
        this.installationId = Objects.requireNonNull(installationId);
        updateRegistration(loginSession, platform, fcmToken, now);
    }

    public static PushDevice register(UUID installationId, LoginSession loginSession, PushPlatform platform,
                                      String fcmToken, Instant now) {
        return new PushDevice(installationId, loginSession, platform, fcmToken, now);
    }

    public void updateRegistration(LoginSession loginSession, PushPlatform platform, String fcmToken, Instant now) {
        Objects.requireNonNull(loginSession);
        Objects.requireNonNull(platform);
        Objects.requireNonNull(now);
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new IllegalArgumentException("FCM 토큰을 입력해야 합니다.");
        }
        this.loginSession = loginSession;
        this.platform = platform;
        this.fcmToken = fcmToken;
        this.updatedAt = now;
        this.disabledAt = null;
    }

    public void disable(Instant now) {
        if (disabledAt == null) {
            this.disabledAt = Objects.requireNonNull(now);
            this.updatedAt = now;
            this.fcmToken = null;
        }
    }
}
