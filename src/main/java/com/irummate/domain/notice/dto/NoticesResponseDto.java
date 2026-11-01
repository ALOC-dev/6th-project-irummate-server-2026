package com.irummate.domain.notice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class NoticesResponseDto {
    private List<NoticeResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static NoticesResponseDto from(Page<NoticeResponseDto> result) {
        return new NoticesResponseDto(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
