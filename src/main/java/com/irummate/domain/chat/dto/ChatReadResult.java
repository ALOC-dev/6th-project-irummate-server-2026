package com.irummate.domain.chat.dto;

public record ChatReadResult(
        ChatReadResponseDto response,
        ChatReadEventDto event
) {
}
