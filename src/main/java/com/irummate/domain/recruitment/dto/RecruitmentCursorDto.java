package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RecruitmentCursorDto {

    // 실제 반환한 마지막 게시글의 정렬 위치. 작성 시각의 소수 초도 그대로 유지
    private final LocalDateTime createdAt;
    private final Long id;

    public static RecruitmentCursorDto from(Recruitments recruitment) {
        return RecruitmentCursorDto.builder()
                .createdAt(recruitment.getCreatedAt())
                .id(recruitment.getId())
                .build();
    }
}
