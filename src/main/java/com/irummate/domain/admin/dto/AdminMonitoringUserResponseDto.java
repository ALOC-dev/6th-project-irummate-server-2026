package com.irummate.domain.admin.dto;

public record AdminMonitoringUserResponseDto(
        String userId,
        String realName,
        String nickname,
        long heartSentCount,
        long heartReceivedCount,
        long heartMatchedCount,
        boolean finalConfirmed,
        long openChatRoomCount,
        long closedChatRoomCount
) {
}
