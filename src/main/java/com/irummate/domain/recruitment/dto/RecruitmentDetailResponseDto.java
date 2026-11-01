package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentDetailResponseDto {

    // 게시글 정보
    private final Long recruitmentId;
    private final String title;
    private final String content;
    private final RecruitmentCategory category;
    private final RecruitmentStatus status;

    // 작성자 정보
    private final Long authorId;
    private final String authorNickname;
    private final String authorProfileImageUrl;

    // 활동 정보
    private final String place;
    private final LocalDateTime activityAt;
    private final LocalDateTime deadlineAt;
    private final Integer estimatedFee;

    // 모집 인원 정보
    private final Integer nowParticipants;
    private final Integer maxParticipants;
    private final boolean full;

    // 현재 로그인 사용자 기준 정보
    private final boolean mine;
    private final boolean participating;
    private final boolean joinable;

    // 게시글 공개 상태
    private final RecruitmentVisibility visibility;
    private final RecruitmentHiddenReason hiddenReason;

    // 상태 변경 정보
    private final String cancelReason;
    private final LocalDateTime closedAt;
    private final LocalDateTime canceledAt;
    private final LocalDateTime completedAt;

    // 작성 및 수정 시각
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static RecruitmentDetailResponseDto from(
            Recruitments recruitment,
            boolean mine,
            boolean participating,
            boolean joinable
    ) {
        boolean full =
                recruitment.getNowParticipants()
                        >= recruitment.getMaxParticipants();

        return RecruitmentDetailResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .title(recruitment.getTitle())
                .content(recruitment.getContent())
                .category(recruitment.getCategory())
                .status(recruitment.getStatus())

                .authorId(recruitment.getAuthor().getId())
                .authorNickname(
                        recruitment.getAuthor().getNickname()
                )
                .authorProfileImageUrl(
                        recruitment.getAuthor().getProfileImageUrl()
                )

                .place(recruitment.getPlace())
                .activityAt(recruitment.getActivityAt())
                .deadlineAt(recruitment.getDeadlineAt())
                .estimatedFee(recruitment.getEstimatedFee())

                .nowParticipants(recruitment.getNowParticipants())
                .maxParticipants(recruitment.getMaxParticipants())
                .full(full)

                .mine(mine)
                .participating(participating)
                .joinable(joinable)

                .visibility(recruitment.getVisibility())
                .hiddenReason(recruitment.getHiddenReason())

                .cancelReason(recruitment.getCancelReason())
                .closedAt(recruitment.getClosedAt())
                .canceledAt(recruitment.getCanceledAt())
                .completedAt(recruitment.getCompletedAt())

                .createdAt(recruitment.getCreatedAt())
                .updatedAt(recruitment.getUpdatedAt())
                .build();
    }
}