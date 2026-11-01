package com.irummate.domain.user.entity;

import com.irummate.domain.survey.entity.UserPreferences;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
        name = "users",
        indexes = @Index(
                name = "idx_users_role_status_created_at",
                columnList = "role, status, created_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    // 사용자 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // OAuth 제공자에서 발급한 사용자 식별자
    @Column(nullable = false, unique = true)
    private String oauthId;

    // 사용자 이메일
    @Column(unique = true)
    private String email;

    // 서비스에서 사용하는 사용자 닉네임
    @Column(nullable = false)
    private String nickname;

    // 사용자 프로필 이미지 주소
    private String profileImageUrl;

    // 사용자의 서비스 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    // 사용자의 계정 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    // 사용자 계정 생성 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 사용자 정보의 마지막 수정 시각
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 사용자 회원 탈퇴 시각
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    // 사용자의 추가 신원 정보
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserDetails userDetails;

    // 사용자의 생활 습관 및 매칭 선호 정보
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
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
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    public void ban() {
        this.status = UserStatus.BANNED;
    }

    public void unban() {
        this.status = UserStatus.ACTIVE;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
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
