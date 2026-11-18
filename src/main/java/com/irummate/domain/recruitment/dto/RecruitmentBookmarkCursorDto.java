package com.irummate.domain.recruitment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RecruitmentBookmarkCursorDto {

    // 마지막으로 반환한 즐겨찾기의 등록 시각과 기록 ID
    private final LocalDateTime bookmarkedAt;
    private final Long id;
}
