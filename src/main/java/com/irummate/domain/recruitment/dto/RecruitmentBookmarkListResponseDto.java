package com.irummate.domain.recruitment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RecruitmentBookmarkListResponseDto {

    private final List<RecruitmentResponseDto> items;
    private final boolean hasNext;

    // 다음 목록이 없으면 null
    private final RecruitmentBookmarkCursorDto nextCursor;
}
