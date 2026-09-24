package com.irummate.global;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.websocket.WebSocketStompErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketStompErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketStompErrorHandler errorHandler = new WebSocketStompErrorHandler(objectMapper);

    @Test
    void serializesBusinessExceptionAsStableErrorPayload() throws Exception {
        Message<byte[]> message = errorHandler.handleClientMessageProcessingError(
                null,
                new RuntimeException(new BusinessException(ErrorCode.CHAT_ROOM_CLOSED))
        );

        JsonNode payload = objectMapper.readTree(message.getPayload());

        assertThat(payload.get("errorCode").asText()).isEqualTo("CHAT_ROOM_CLOSED");
        assertThat(payload.get("message").asText()).isEqualTo(ErrorCode.CHAT_ROOM_CLOSED.getMessage());
        assertThat(new String(message.getPayload())).doesNotContain("RuntimeException");
    }

    @Test
    void hidesUnexpectedInternalException() throws Exception {
        Message<byte[]> message = errorHandler.handleClientMessageProcessingError(
                null,
                new IllegalStateException("database password must not leak")
        );

        JsonNode payload = objectMapper.readTree(message.getPayload());

        assertThat(payload.get("errorCode").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(new String(message.getPayload())).doesNotContain("password");
    }
}
