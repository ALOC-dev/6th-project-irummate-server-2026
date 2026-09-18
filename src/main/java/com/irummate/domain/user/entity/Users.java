package com.irummate.domain.user.entity;

import com.irummate.domain.survey.entity.UserPreferences;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String oauthId;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.GUEST; // GUEST, USER, ADMIN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING; // ACTIVE, PENDING, BANNED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserDetails userDetails;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserPreferences userPreferences;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    @Builder
    public Users(String oauthId, String email, String nickname, String profileImageUrl) {
        this.oauthId = oauthId;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.role = UserRole.GUEST;
        this.status = UserStatus.PENDING;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void ban() {
        this.status = UserStatus.BANNED;
    }

    public void unban() {
        this.status = UserStatus.ACTIVE;
    }

    public void promoteToUser() {
        this.role = UserRole.USER;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.oauthId = this.oauthId + "_withdrawn_" + System.currentTimeMillis();
        this.nickname = "\uD0C8\uD1F4\uD55C \uC0AC\uC6A9\uC790";
        this.email = null;
        this.profileImageUrl = null;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
