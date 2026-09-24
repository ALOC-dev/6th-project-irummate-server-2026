package com.irummate.global.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

@Component
public class WebSocketStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    public WebSocketStompErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        BusinessException businessException = findBusinessException(exception);
        ErrorCode errorCode = businessException == null
                ? ErrorCode.INTERNAL_ERROR
                : businessException.getErrorCode();
        String message = businessException == null
                ? errorCode.getMessage()
                : businessException.getMessage();

        WebSocketErrorResponseDto response = new WebSocketErrorResponseDto(errorCode.name(), message);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setContentType(MediaType.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(toPayload(response), accessor.getMessageHeaders());
    }

    private BusinessException findBusinessException(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            current = current.getCause();
        }

        return null;
    }

    private byte[] toPayload(WebSocketErrorResponseDto response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException e) {
            return ("{\"errorCode\":\"INTERNAL_ERROR\",\"message\":\""
                    + ErrorCode.INTERNAL_ERROR.getMessage()
                    + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
