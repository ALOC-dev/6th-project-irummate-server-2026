package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentParticipants;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentParticipantRemoveResponseDto {

    private final Long recruitmentId;

    // 강제 제외된 사용자의 공개 ID(HashIds)와 처리 결과
    private final String targetUserId;
    private final RecruitmentParticipantStatus participantStatus;
    private final String removalReason;
    private final LocalDateTime leftAt;

    // 작성자를 포함한 처리 후 인원 및 자동 재개 결과
    private final Integer nowParticipants;
    private final Integer maxParticipants;
    private final RecruitmentStatus status;

    // 강제 제외 및 모집 인원 감소 후 호출합니다.
    public static RecruitmentParticipantRemoveResponseDto from(
            RecruitmentParticipants participant,
            String targetUserId
    ) {
        Recruitments recruitment = participant.getRecruitment();

        return RecruitmentParticipantRemoveResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .targetUserId(targetUserId)
                .participantStatus(participant.getStatus())
                .removalReason(participant.getRemovalReason())
                .leftAt(participant.getLeftAt())
                .nowParticipants(recruitment.getNowParticipants())
                .maxParticipants(recruitment.getMaxParticipants())
                .status(recruitment.getStatus())
                .build();
    }
}
