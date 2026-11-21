package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentHiddenReason;
import com.irummate.domain.recruitment.entity.RecruitmentPriceType;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Builder
@AllArgsConstructor
public class RecruitmentResponseDto {

    // 게시글 정보
    private final Long recruitmentId;
    private final String title;
    private final RecruitmentCategory category;
    private final RecruitmentStatus status;

    // 게시글 공개 상태 및 숨김 사유
    private final RecruitmentVisibility visibility;
    private final RecruitmentHiddenReason hiddenReason;

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
    // 올림 계산한 예상 1인 금액 (실제 정산액 아님)
    private final Integer estimatedFee;

    // 모집 인원 정보
    private final Integer nowParticipants;
    private final Integer maxParticipants;
    private final boolean full;

    // 작성 시각
    private final LocalDateTime createdAt;

    // 현재 로그인 사용자와 모집글의 관계
    private final RecruitmentParticipationView participating;
    private final boolean joinable;
    private final boolean bookmarked;

    public static RecruitmentResponseDto from(
            Recruitments recruitment,
            RecruitmentParticipationView participating,
            boolean bookmarked
    ) {
        return from(recruitment, participating, recruitment.getAuthor().getId(),
                recruitment.getAuthor().getNickname(), recruitment.getAuthor().getProfileImageUrl(), bookmarked);
    }

    // 작성자 필드를 함께 조회한 경우 Users 추가 조회 없이 응답 생성
    public static RecruitmentResponseDto from(
            Recruitments recruitment,
            RecruitmentParticipationView participating,
            Long authorId,
            String authorNickname,
            String authorProfileImageUrl,
            boolean bookmarked
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        boolean joinable = participating.isJoinable(recruitment, now);
        boolean full = recruitment.getNowParticipants()
                >= recruitment.getMaxParticipants();

        return RecruitmentResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .title(recruitment.getTitle())
                .category(recruitment.getCategory())
                .status(recruitment.getStatus())
                .visibility(recruitment.getVisibility())
                .hiddenReason(
                        recruitment.getVisibility() == RecruitmentVisibility.HIDDEN
                                ? recruitment.getHiddenReason()
                                : null
                )

                .authorId(authorId)
                .authorNickname(authorNickname)
                .authorProfileImageUrl(authorProfileImageUrl)

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

                .createdAt(recruitment.getCreatedAt())
                .build();
    }
}
