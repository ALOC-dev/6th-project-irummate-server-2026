package com.irummate.global.websocket;

public record WebSocketErrorResponseDto(
        String errorCode,
        String message
) {
}
