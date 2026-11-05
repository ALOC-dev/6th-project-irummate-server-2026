package com.irummate.domain.recruitment.entity;

import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

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

    // 모집 설명과 참여 조건
    @Column(name = "content", length = 80)
    private String content;

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


    // 예상 비용(공동 구매 시)
    @Column(name = "estimated_fee")
    private Integer estimatedFee;


    // 취소 시 사유
    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;


    // 아래 3개의 일자는 모집글 상태 변화에 따른 날짜

    // 1. 모집 마감 시각
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // 2. 모집 취소 시각
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 3. 활동 완료 시각
    @Column(name = "completed_at")
    private LocalDateTime completedAt;



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
            String content,
            String place,
            LocalDateTime activityAt,
            LocalDateTime deadlineAt,
            Integer maxParticipants,
            Integer estimatedFee,
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

        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (place != null) this.place = place;
        if (activityAt != null) this.activityAt = activityAt;
        if (deadlineAt != null) this.deadlineAt = deadlineAt;
        if (maxParticipants != null) this.maxParticipants = maxParticipants;
        if (estimatedFee != null) this.estimatedFee = estimatedFee;

        if (this.status == RecruitmentStatus.OPEN
                && this.nowParticipants >= this.maxParticipants) {
            close(now);
        }
    }

    public void increaseParticipants(LocalDateTime now) {
        this.nowParticipants += 1;

        if (this.nowParticipants >= this.maxParticipants) {
            close(now);
        }
    }

    public void close(LocalDateTime now) {
        if (this.status != RecruitmentStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        this.status = RecruitmentStatus.CLOSED;
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

    public void complete(LocalDateTime now) {
        if ((this.status != RecruitmentStatus.OPEN
                && this.status != RecruitmentStatus.CLOSED)
                || now.isBefore(this.activityAt)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        if (this.status == RecruitmentStatus.OPEN) {
            close(now);
        }

        this.status = RecruitmentStatus.COMPLETED;
        this.completedAt = now;
    }
}
