package com.irummate.domain.notice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NoticeIngestResponseDto {
    private int inserted;
    private int updated;
    private int unchanged;
}
