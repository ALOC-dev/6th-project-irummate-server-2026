package com.irummate.domain.matching.entity;

import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Table(name = "match_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_match_requests_user_pair",
                        columnNames = {"user_low_id", "user_high_id"}
                )
        },
        // user_low_id 조건은 유니크 인덱스(user_low_id, user_high_id)가 처리하고,
        // user_high_id 조건은 이 인덱스가 처리해 "user_low_id = ? OR user_high_id = ?" 조회가 인덱스를 사용
        indexes = {
                @Index(
                        name = "idx_match_requests_high_low",
                        columnList = "user_high_id, user_low_id"
                )
        })
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_request_id")
    private Long id;

    // Id가 더 작은 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_low_id")
    private Users userLow;

    // Id가 더 큰 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_high_id")
    private Users userHigh;

    // userLow의 설문 내역
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_low_preferences_id")
    private UserPreferences userLowPreferences;

    // userHigh의 설문 내역
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_high_preferences_id")
    private UserPreferences userHighPreferences;

    // 둘 사이의 매칭 점수
    @Column
    private Double matchPercentage;

    // userLow의 매칭 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "user_low_status")
    private MatchStatus userLowStatus;

    // userHigh의 매칭 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "user_high_status")
    private MatchStatus userHighStatus;

    // matchRequest가 생성된 시간(Clock 기준 자동 기록, JpaAuditingConfig)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // userLow가 추천받은 시간
    @Column(name = "user_low_recommended_at")
    private LocalDateTime userLowRecommendedAt;

    // userHigh가 추천받은 시간
    @Column(name = "user_high_recommended_at")
    private LocalDateTime userHighRecommendedAt;


    public MatchStatus getStatusOf(Long userId) {
        if (userLow.getId().equals(userId)) {
            return userLowStatus;
        }
        if (userHigh.getId().equals(userId)) {
            return userHighStatus;
        }
        throw new IllegalArgumentException("User is not part of this match request.");
    }

    // 특정 사용자의 matchStatus를 업데이트
    // userlow, userhigh는 함수가 구분
    // RECOMMENDED는 추천 시각이 필요하므로 recommend(userId, now)를 사용
    public void updateStatusOf(Long userId, MatchStatus status) {
        if (status == MatchStatus.RECOMMENDED) {
            throw new IllegalArgumentException("RECOMMENDED status must be set via recommend(userId, now).");
        }

        if (userLow.getId().equals(userId)) {
            this.userLowStatus = status;
            return;
        }

        if (userHigh.getId().equals(userId)) {
            this.userHighStatus = status;
            return;
        }

        throw new IllegalArgumentException("User is not part of this match request.");
    }

    // 특정 사용자에게 RECOMMENDED 상태와 추천 시각을 기록
    public void recommend(Long userId, LocalDateTime now) {
        if (userLow.getId().equals(userId)) {
            this.userLowStatus = MatchStatus.RECOMMENDED;
            this.userLowRecommendedAt = now;
            return;
        }

        if (userHigh.getId().equals(userId)) {
            this.userHighStatus = MatchStatus.RECOMMENDED;
            this.userHighRecommendedAt = now;
            return;
        }

        throw new IllegalArgumentException("User is not part of this match request.");
    }

    public boolean isHeartMatched() {
        return userLowStatus == MatchStatus.HEART
                && userHighStatus == MatchStatus.HEART;
    }

    public boolean isConfirmed() {
        return userLowStatus == MatchStatus.FINAL_CONFIRMED
                && userHighStatus == MatchStatus.FINAL_CONFIRMED;
    }

    public boolean isRejected() {
        return userLowStatus == MatchStatus.REJECTED
                || userHighStatus == MatchStatus.REJECTED;
    }

    public void closeForWithdrawal(Long userId) {
        updateStatusOf(userId, MatchStatus.CLOSED);
        if (userLow.getId().equals(userId)) {
            this.userLowPreferences = null;
        } else if (userHigh.getId().equals(userId)) {
            this.userHighPreferences = null;
        }
    }
}
