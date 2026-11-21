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
    private final RecruitmentPriceType priceType;
    private final Integer amount;
    // 참여 가능한 미참여자는 참여 후, 나머지는 현재 인원 기준으로 계산
    private final Integer estimatedFee;

    // 모집 인원 정보
    private final Integer nowParticipants;
    private final Integer maxParticipants;
    private final boolean full;

    // 현재 로그인 사용자 기준 정보
    private final RecruitmentParticipationView participating;
    private final boolean joinable;
    private final boolean bookmarked;

    // 현재 로그인 사용자 본인의 강제 제외 사유
    private final String removalReason;

    // 게시글 공개 상태
    private final RecruitmentVisibility visibility;
    private final RecruitmentHiddenReason hiddenReason;

    // 상태 변경 정보
    private final String cancelReason;
    private final LocalDateTime closedAt;
    private final LocalDateTime canceledAt;
    private final LocalDateTime endedAt;

    // 작성 및 수정 시각
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static RecruitmentDetailResponseDto from(
            Recruitments recruitment,
            RecruitmentParticipationView participating,
            String removalReason,
            boolean bookmarked,
            LocalDateTime now
    ) {
        // now: 참여 가능 여부 판단 기준 시각(서비스에서 주입된 Clock으로 계산해 전달)
        boolean joinable = participating.isJoinable(recruitment, now);
        boolean full =
                recruitment.getNowParticipants()
                        >= recruitment.getMaxParticipants();

        return RecruitmentDetailResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .title(recruitment.getTitle())
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
                .priceType(recruitment.getPriceType())
                .amount(recruitment.getAmount())
                .estimatedFee(recruitment.calculateEstimatedFee(
                        joinable,
                        now
                ))

                .nowParticipants(recruitment.getNowParticipants())
                .maxParticipants(recruitment.getMaxParticipants())
                .full(full)

                .participating(participating)
                .joinable(joinable)
                .bookmarked(bookmarked)
                .removalReason(participating == RecruitmentParticipationView.REMOVED
                        ? removalReason : null)

                .visibility(recruitment.getVisibility())
                .hiddenReason(recruitment.getHiddenReason())

                .cancelReason(recruitment.getCancelReason())
                .closedAt(recruitment.getClosedAt())
                .canceledAt(recruitment.getCanceledAt())
                .endedAt(recruitment.getEndedAt())

                .createdAt(recruitment.getCreatedAt())
                .updatedAt(recruitment.getUpdatedAt())
                .build();
    }
}
