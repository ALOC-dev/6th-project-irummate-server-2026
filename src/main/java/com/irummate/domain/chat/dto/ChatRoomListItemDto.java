package com.irummate.domain.chat.dto;

import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;

public record ChatRoomListItemDto(
        Long roomId,
        ChatRoomType type,
        Long partnerId,
        String partnerName,
        String partnerProfileImageUrl,
        ChatRoomStatus status,
        MatchStatus myMatchStatus,
        MatchStatus partnerMatchStatus,
        Long recruitmentId,
        String recruitmentTitle,
        RecruitmentCategory recruitmentCategory,
        Integer participantCount
) {
}
