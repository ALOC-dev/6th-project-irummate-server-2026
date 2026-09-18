package com.irummate.domain.chat.dto;

import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.matching.entity.MatchStatus;

public record ChatRoomPartnerDto(
        Long roomId,
        Long partnerId,
        String partnerName,
        String partnerProfileImageUrl,
        ChatRoomStatus status,
        MatchStatus myMatchStatus,
        MatchStatus partnerMatchStatus
) {
}
