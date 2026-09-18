package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatUnreadCountResponseDto {
    private Integer totalUnreadCount;
}