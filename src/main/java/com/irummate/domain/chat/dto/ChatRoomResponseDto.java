package com.irummate.domain.chat.dto;

import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.matching.dto.MatchCardStatus;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatRoomResponseDto { //JSON 응답 Key 설정 

    private Long roomId;
    private ChatRoomType type;
    private String partnerId;
    private String partnerName;
    private String partnerProfileImageUrl;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private ChatRoomStatus status;
    private MatchCardStatus matchStatus;
    private Boolean confirmedByMe;
    private Boolean canConfirm;
    private RecruitmentInfo recruitment;

    public record RecruitmentInfo(Long recruitmentId, String title, RecruitmentCategory category, Integer participantCount) {
    }
}
