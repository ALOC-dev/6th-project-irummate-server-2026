package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentCreateResponseDto {

    private final Long recruitmentId;
    private final RecruitmentStatus status;
    private final LocalDateTime createdAt;

    // 쉬운 변환을 위한 함수
    public static RecruitmentCreateResponseDto from(Recruitments recruitment) {
        return RecruitmentCreateResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .status(recruitment.getStatus())
                .createdAt(recruitment.getCreatedAt())
                .build();
    }
}
