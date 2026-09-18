package com.irummate.domain.chat.dto;

public record ChatRoomUnreadCountDto(
        Long roomId,
        Long unreadCount
) {
}