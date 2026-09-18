package com.irummate.domain.admin.dto;

import java.time.LocalDateTime;

public record AdminMonitoringSummaryResponseDto(
        MatchingSummary matching,
        ChatSummary chat,
        LocalDateTime updatedAt
) {
    public record MatchingSummary(
            long heartSent,
            long heartMatched,
            long confirmPending,
            long finalConfirmed,
            long closed
    ) {
    }

    public record ChatSummary(
            long totalRooms,
            long openRooms,
            long closedRooms,
            long totalMessages,
            long messagesToday
    ) {
    }
}
