package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatMessageEventDto {

    private final ChatEventType type;
    private final Long roomId;
    private final Long messageId;
    private final String senderId;
    private final String message;
    private final LocalDateTime createdAt;
    private final Boolean isRead;

    public static ChatMessageEventDto from(Long roomId, ChatMessageResponseDto message) {
        return new ChatMessageEventDto(
                ChatEventType.MESSAGE,
                roomId,
                message.getMessageId(),
                message.getSenderId(),
                message.getMessage(),
                message.getCreatedAt(),
                message.getIsRead()
        );
    }
}
