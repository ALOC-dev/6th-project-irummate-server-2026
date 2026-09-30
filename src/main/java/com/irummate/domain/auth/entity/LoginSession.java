package com.irummate.domain.auth.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "login_sessions", indexes = {
        @Index(name = "idx_login_sessions_user", columnList = "user_id"),
        @Index(name = "idx_login_sessions_expires", columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public LoginSession(Users user, String refreshTokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    public boolean isAvailable(LocalDateTime time) {
        return revokedAt == null && expiresAt.isAfter(time);
    }

    public void rotate(String newHash, LocalDateTime newExpiresAt) {
        this.refreshTokenHash = newHash;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = now();
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = now();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
