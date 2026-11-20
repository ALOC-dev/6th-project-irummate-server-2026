package com.irummate.domain.recruitment.entity;

import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Table(
        name = "recruitment_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recruitment_participant",
                columnNames = {"recruitment_id", "user_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_recruitment_participant_user_status",
                        columnList = "user_id, status"
                ),
                @Index(
                        name = "idx_recruitment_participant_recruitment_status",
                        columnList = "recruitment_id, status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentParticipants {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitments recruitment;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private RecruitmentParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecruitmentParticipantStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // 강제 제외 사유 (자진 나가기는 null)
    @Column(name = "removal_reason", length = 100)
    private String removalReason;

    // 생성/수정 시각(Clock 기준 자동 기록, JpaAuditingConfig)
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    private RecruitmentParticipants(
            Recruitments recruitment,
            Users user,
            RecruitmentParticipantRole role,
            LocalDateTime joinedAt
    ) {
        this.recruitment = recruitment;
        this.user = user;
        this.role = role;
        this.status = RecruitmentParticipantStatus.JOINED;
        this.joinedAt = joinedAt;
        this.leftAt = null;
    }

    public static RecruitmentParticipants createHost(
            Recruitments recruitment,
            Users author,
            LocalDateTime joinedAt
    ) {
        return new RecruitmentParticipants(
                recruitment,
                author,
                RecruitmentParticipantRole.HOST,
                joinedAt
        );
    }

    public static RecruitmentParticipants joinAsMember(
            Recruitments recruitment,
            Users user,
            LocalDateTime joinedAt
    ) {
        return new RecruitmentParticipants(
                recruitment,
                user,
                RecruitmentParticipantRole.MEMBER,
                joinedAt
        );
    }

    public void leave(LocalDateTime now) {
        if (this.role == RecruitmentParticipantRole.HOST) {
            throw new BusinessException(ErrorCode.RECRUITMENT_HOST_CANNOT_LEAVE);
        }
        if (this.status != RecruitmentParticipantStatus.JOINED) {
            throw new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_ACTIVE);
        }

        this.status = RecruitmentParticipantStatus.LEFT;
        this.leftAt = now;
    }

    public void remove(String reason, LocalDateTime now) {
        if (this.role == RecruitmentParticipantRole.HOST) {
            throw new BusinessException(ErrorCode.RECRUITMENT_HOST_CANNOT_BE_REMOVED);
        }
        if (this.status != RecruitmentParticipantStatus.JOINED) {
            throw new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_ACTIVE);
        }
        String normalizedReason = reason == null ? null : reason.strip();
        if (normalizedReason == null || normalizedReason.isEmpty() || normalizedReason.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        this.status = RecruitmentParticipantStatus.REMOVED;
        this.leftAt = now;
        this.removalReason = normalizedReason;
    }

}
