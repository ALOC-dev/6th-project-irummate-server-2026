package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RecruitmentResponseDto {

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

    // 작성 시각
    private final LocalDateTime createdAt;

    // 현재 로그인 사용자와 모집글의 관계
    private final ParticipationView participating;

    public enum ParticipationView {
        MINE,
        JOINED,
        NONE
    }

    public static RecruitmentResponseDto from(
            Recruitments recruitment,
            ParticipationView participating
    ) {

        boolean full = recruitment.getNowParticipants()
                >= recruitment.getMaxParticipants();

        return RecruitmentResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .title(recruitment.getTitle())
                .content(recruitment.getContent())
                .category(recruitment.getCategory())
                .status(recruitment.getStatus())

                .authorId(recruitment.getAuthor().getId())
                .authorNickname(recruitment.getAuthor().getNickname())
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
                .participating(participating)

                .createdAt(recruitment.getCreatedAt())
                .build();
    }
}
