package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatReadEventDto {

    private final ChatEventType type;
    private final Long roomId;
    private final String readerId;
    private final Long lastReadMessageId;

    public static ChatReadEventDto of(Long roomId, String readerId, Long lastReadMessageId) {
        return new ChatReadEventDto(ChatEventType.READ, roomId, readerId, lastReadMessageId);
    }
}
