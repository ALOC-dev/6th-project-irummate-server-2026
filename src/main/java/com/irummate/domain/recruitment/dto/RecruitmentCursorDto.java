package com.irummate.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irummate.domain.recruitment.entity.Recruitments;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecruitmentCursorDto {

    // 정렬에 해당하는 시각만 반환. 소수 초도 그대로 유지
    private final LocalDateTime createdAt;
    private final LocalDateTime deadlineAt;
    private final Long id;

    public static RecruitmentCursorDto from(Recruitments recruitment, RecruitmentSort sort) {
        return RecruitmentCursorDto.builder()
                .createdAt(sort == RecruitmentSort.LATEST ? recruitment.getCreatedAt() : null)
                .deadlineAt(sort == RecruitmentSort.CLOSING_SOON ? recruitment.getDeadlineAt() : null)
                .id(recruitment.getId())
                .build();
    }
}
