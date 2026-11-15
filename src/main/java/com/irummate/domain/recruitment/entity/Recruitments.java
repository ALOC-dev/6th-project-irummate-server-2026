package com.irummate.domain.recruitment.entity;

import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Table(name = "recruitments")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recruitments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 게시글 저자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    // 게시글 제목
    @Column(name = "title", nullable = false)
    private String title;

    // 게시글 유형
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecruitmentCategory category;

    // 게시글 상태
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecruitmentStatus status;


    // 게시글 공개 상태
    @Column(name = "visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecruitmentVisibility visibility;

    // 비공개 시 사유
    @Enumerated(EnumType.STRING)
    private RecruitmentHiddenReason hiddenReason;

    // 대략적인 활동 장소 혹은 만남 장소
    @Column(name = "place")
    private String place;

    // 활동 일시
    @Column(name = "activity_at", nullable = false)
    private LocalDateTime activityAt;

    // 모집 마감 일시
    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;


    // 현재 모집 인원
    // select 대신 인원 계산을 위한 컬럼
    @Column(name = "now_participants", nullable = false)
    private Integer nowParticipants;

    // 최대 모집 인원(모집자 포함)
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;


    // 공동구매 금액 방식
    @Enumerated(EnumType.STRING)
    @Column(name = "price_type")
    private RecruitmentPriceType priceType;

    // FIXED는 1인 금액, SPLIT은 총액
    @Column(name = "amount")
    private Integer amount;


    // 취소 시 사유
    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;


    // 아래 3개의 일자는 모집글 상태 변화에 따른 날짜

    // 1. 모집 마감 시각
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // 모집 마감 사유 (수동 마감 / 정원 충족)
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private RecruitmentCloseReason closeReason;

    // 2. 모집 취소 시각
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 3. 활동 시작에 따른 게시판 종료 처리 시각 (기존 DB 컬럼명 유지)
    @Column(name = "completed_at")
    private LocalDateTime endedAt;



    // 게시글 첫 작성 일자
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    // 게시글 마지막 수정 일자
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    @PrePersist
    protected void onCreate() {
        LocalDateTime now =
                LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt =
                LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    // null인 항목은 유지하고, 전달된 항목만 수정합니다.
    public void update(
            String title,
            String place,
            LocalDateTime activityAt,
            LocalDateTime deadlineAt,
            Integer maxParticipants,
            RecruitmentPriceType priceType,
            Integer amount,
            LocalDateTime now
    ) {
        if (this.visibility != RecruitmentVisibility.VISIBLE) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
        if ((this.status != RecruitmentStatus.OPEN
                && this.status != RecruitmentStatus.CLOSED)
                || !this.activityAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }
        if ((activityAt != null && !activityAt.isAfter(now))
                || (deadlineAt != null && !deadlineAt.isAfter(now))) {
            throw new BusinessException(ErrorCode.PAST_DATE_NOT_ALLOWED);
        }

        LocalDateTime updatedActivityAt = activityAt != null ? activityAt : this.activityAt;
        LocalDateTime updatedDeadlineAt = deadlineAt != null ? deadlineAt : this.deadlineAt;
        if (!updatedDeadlineAt.isBefore(updatedActivityAt)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_PERIOD);
        }
        if (maxParticipants != null
                && (maxParticipants < 2 || maxParticipants < this.nowParticipants)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        RecruitmentPriceType updatedPriceType = priceType != null ? priceType : this.priceType;
        Integer updatedAmount = amount != null ? amount : this.amount;
        validatePrice(updatedPriceType, updatedAmount);

        // 생략한 항목은 유지하고, 전달된 문자열은 정리와 검증을 마친 뒤 반영
        String updatedTitle = title != null ? normalizeTitle(title) : this.title;
        String updatedPlace = place != null ? normalizePlace(place) : this.place;

        this.title = updatedTitle;
        this.place = updatedPlace;
        if (activityAt != null) this.activityAt = activityAt;
        if (deadlineAt != null) this.deadlineAt = deadlineAt;
        if (maxParticipants != null) this.maxParticipants = maxParticipants;
        this.priceType = updatedPriceType;
        this.amount = updatedAmount;

        if (this.status == RecruitmentStatus.OPEN
                && this.nowParticipants >= this.maxParticipants) {
            close(RecruitmentCloseReason.FULL, now);
        }

        // 정원 수정으로 빈자리가 생긴 경우 정원 마감만 자동 재개
        if (maxParticipants != null) {
            reopenIfFullAndAvailable(now);
        }
    }

    // 생성 시 문자열 정리. DTO를 거치지 않는 호출에서도 같은 기준 적용
    public void configureText(String title, String place) {
        String normalizedTitle = normalizeTitle(title);
        String normalizedPlace = normalizePlace(place);

        this.title = normalizedTitle;
        this.place = normalizedPlace;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String normalizedTitle = title.strip();
        if (normalizedTitle.isBlank() || normalizedTitle.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedTitle;
    }

    private String normalizePlace(String place) {
        if (place == null) {
            return null;
        }

        String normalizedPlace = place.strip();
        if (normalizedPlace.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedPlace.isEmpty() ? null : normalizedPlace;
    }

    // 생성 시 금액 검증. 일반 카테고리는 금액을 사용하지 않음
    public void configurePrice(RecruitmentPriceType priceType, Integer amount) {
        validatePrice(priceType, amount);
        this.priceType = priceType;
        this.amount = amount;
    }

    private void validatePrice(RecruitmentPriceType priceType, Integer amount) {
        if (this.category == RecruitmentCategory.GROUP_BUY) {
            if (priceType == null || amount == null || amount <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        } else if (priceType != null || amount != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public boolean isVisible() {
        return this.visibility == RecruitmentVisibility.VISIBLE;
    }

    // 게시글 자체의 참여 불가 사유. 참여 가능하면 null 반환
    // 실제 참여 API의 기존 검증 순서(숨김 -> 상태 -> 마감 -> 정원)를 유지
    public ErrorCode findJoinError(LocalDateTime now) {
        if (!isVisible()) {
            return ErrorCode.RECRUITMENT_NOT_FOUND;
        }
        if (this.status != RecruitmentStatus.OPEN) {
            return ErrorCode.RECRUITMENT_NOT_OPEN;
        }
        if (!this.deadlineAt.isAfter(now)) {
            return ErrorCode.RECRUITMENT_DEADLINE_PASSED;
        }
        if (this.nowParticipants >= this.maxParticipants) {
            return ErrorCode.RECRUITMENT_FULL;
        }
        return null;
    }

    // 조회와 예상 금액 계산에서 사용. 기존 활동 시작 시각 검사도 유지
    public boolean canAcceptParticipant(LocalDateTime now) {
        return findJoinError(now) == null && this.activityAt.isAfter(now);
    }

    // DB에는 기준 금액만 저장하고 예상 1인 금액은 조회 시 계산
    // 신규 참여 가능 사용자에게만 본인 1명을 더한 예상 금액을 보여줌
    public Integer calculateEstimatedFee(boolean canJoin, LocalDateTime now) {
        if (this.category != RecruitmentCategory.GROUP_BUY
                || this.priceType == null || this.amount == null) {
            return null;
        }
        if (this.priceType == RecruitmentPriceType.FIXED) {
            return this.amount;
        }

        long count = this.nowParticipants;
        if (canJoin && canAcceptParticipant(now)) {
            count += 1;
        }
        // 원 단위 올림. 실제 정산액이 아닌 예상 금액
        return (int) ((this.amount.longValue() + count - 1) / count);
    }

    public void increaseParticipants(LocalDateTime now) {
        this.nowParticipants += 1;

        if (this.nowParticipants >= this.maxParticipants) {
            close(RecruitmentCloseReason.FULL, now);
        }
    }

    public void decreaseParticipants(LocalDateTime now) {
        if (this.nowParticipants <= 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.nowParticipants -= 1;

        reopenIfFullAndAvailable(now);
    }

    // 인원 감소와 정원 수정에 동일한 자동 재개 조건 적용
    private void reopenIfFullAndAvailable(LocalDateTime now) {
        if (this.status == RecruitmentStatus.CLOSED
                && this.closeReason == RecruitmentCloseReason.FULL
                && this.nowParticipants < this.maxParticipants
                && this.visibility == RecruitmentVisibility.VISIBLE
                && this.deadlineAt.isAfter(now)
                && this.activityAt.isAfter(now)) {
            reopen(now);
        }
    }

    public void close(LocalDateTime now) {
        close(RecruitmentCloseReason.MANUAL, now);
    }

    // 모집 마감 시각 경과로 마감. 활동 시작 이후에는 end()로 처리
    public void closeByDeadline(LocalDateTime now) {
        if (this.deadlineAt.isAfter(now) || !this.activityAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        close(RecruitmentCloseReason.DEADLINE, now);
    }

    private void close(RecruitmentCloseReason reason, LocalDateTime now) {
        if (this.status != RecruitmentStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        this.status = RecruitmentStatus.CLOSED;
        this.closeReason = reason;
        this.closedAt = now;
    }

    public void reopen(LocalDateTime now) {
        if (this.visibility != RecruitmentVisibility.VISIBLE) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }
        if (this.status != RecruitmentStatus.CLOSED || !this.activityAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }
        if (!this.deadlineAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.RECRUITMENT_DEADLINE_PASSED);
        }
        if (this.nowParticipants >= this.maxParticipants) {
            throw new BusinessException(ErrorCode.RECRUITMENT_FULL);
        }

        this.status = RecruitmentStatus.OPEN;
        this.closedAt = null;
        this.closeReason = null;
    }

    public void cancel(String reason, LocalDateTime now) {
        if ((this.status != RecruitmentStatus.OPEN
                && this.status != RecruitmentStatus.CLOSED)
                || !this.activityAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        String normalizedReason = reason == null || reason.isBlank() ? null : reason.strip();
        if (normalizedReason != null && normalizedReason.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = RecruitmentStatus.CANCELLED;
        this.cancelReason = normalizedReason;
        this.canceledAt = now;
    }

    public void end(LocalDateTime now) {
        if ((this.status != RecruitmentStatus.OPEN
                && this.status != RecruitmentStatus.CLOSED)
                || now.isBefore(this.activityAt)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        if (this.status == RecruitmentStatus.OPEN) {
            this.closedAt = now;
        }

        this.status = RecruitmentStatus.ENDED;
        this.endedAt = now;
    }
}
