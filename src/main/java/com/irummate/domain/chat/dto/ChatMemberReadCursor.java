package com.irummate.domain.chat.dto;

public record ChatMemberReadCursor(Long userId, Long lastReadMessageId) {
}
