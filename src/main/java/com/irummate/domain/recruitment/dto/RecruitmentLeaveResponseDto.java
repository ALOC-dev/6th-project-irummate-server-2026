package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentParticipants;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentLeaveResponseDto {

    private final Long recruitmentId;

    // 나가기 처리 후 내 참여 상태와 나간 시각
    private final RecruitmentParticipationView participating;
    private final LocalDateTime leftAt;

    // 작성자를 포함한 처리 후 인원
    private final Integer nowParticipants;
    private final Integer maxParticipants;
    private final RecruitmentStatus status;

    // 나간 사용자는 같은 모집글에 재참여할 수 없음
    private final boolean joinable;

    // 참여 상태 변경과 모집 인원 감소 후 호출합니다.
    public static RecruitmentLeaveResponseDto from(RecruitmentParticipants participant) {
        Recruitments recruitment = participant.getRecruitment();

        return RecruitmentLeaveResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .participating(RecruitmentParticipationView.from(false, participant.getStatus()))
                .leftAt(participant.getLeftAt())
                .nowParticipants(recruitment.getNowParticipants())
                .maxParticipants(recruitment.getMaxParticipants())
                .status(recruitment.getStatus())
                .joinable(false)
                .build();
    }
}
