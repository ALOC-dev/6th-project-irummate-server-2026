package com.irummate.domain.recruitment.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

}
