package com.irummate.domain.notice.dto;

import com.irummate.domain.notice.entity.DormitoryNotice;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class NoticeResponseDto {
    private Long id;
    private String title;
    private LocalDate publishedAt;
    private String url;

    public static NoticeResponseDto from(DormitoryNotice notice) {
        return new NoticeResponseDto(notice.getId(), notice.getTitle(), notice.getPublishedAt(), notice.getUrl());
    }
}
