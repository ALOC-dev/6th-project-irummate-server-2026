package com.irummate.domain.admin.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "user_bans", indexes = @Index(name = "idx_user_bans_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banned_by", nullable = false)
    private Users bannedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "banned_at", nullable = false, updatable = false)
    private LocalDateTime bannedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by")
    private Users liftedBy;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    public UserBan(Users user, Users bannedBy, String reason, LocalDateTime expiresAt) {
        this.user = user;
        this.bannedBy = bannedBy;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.bannedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void lift(Users admin) {
        this.liftedBy = admin;
        this.liftedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
