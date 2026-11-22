package com.irummate.chat;

import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatMessageSendRequestDto;
import com.irummate.domain.chat.controller.ChatWebSocketController;
import com.irummate.domain.chat.service.ChatDelivery;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.websocket.WebSocketErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketControllerTest {

    @Test
    void forwardsSavedMessageToChatDelivery() {
        ChatService chatService = mock(ChatService.class);
        ChatDelivery delivery = mock(ChatDelivery.class);
        ChatWebSocketController controller = new ChatWebSocketController(chatService, delivery);
        ChatMessageSendRequestDto request = mock(ChatMessageSendRequestDto.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 24, 20, 0);
        ChatMessageResponseDto response = new ChatMessageResponseDto(
                106L,
                "hashed-sender",
                "hello",
                createdAt,
                false
        );
        when(request.getRoomId()).thenReturn(7L);
        when(request.getMessage()).thenReturn("hello");
        when(chatService.sendMessage(7L, 1L, "hello")).thenReturn(response);

        controller.sendMessage(request, new WebSocketPrincipal(1L));

        verify(delivery).publishMessage(7L, 1L, response);
    }

    @Test
    void convertsSendBusinessExceptionToSessionErrorPayload() throws Exception {
        ChatService chatService = mock(ChatService.class);
        ChatWebSocketController controller = new ChatWebSocketController(chatService, mock(ChatDelivery.class));
        ChatMessageSendRequestDto request = mock(ChatMessageSendRequestDto.class);

        when(request.getRoomId()).thenReturn(7L);
        when(request.getMessage()).thenReturn("hello");
        when(chatService.sendMessage(7L, 1L, "hello"))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_CLOSED));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> controller.sendMessage(request, new WebSocketPrincipal(1L))
        );
        WebSocketErrorResponseDto response = controller.handleChatBusinessException(exception);

        assertThat(response.errorCode()).isEqualTo("CHAT_ROOM_CLOSED");
        assertThat(response.message()).isEqualTo(ErrorCode.CHAT_ROOM_CLOSED.getMessage());

        Method handler = ChatWebSocketController.class.getMethod(
                "handleChatBusinessException",
                BusinessException.class
        );
        assertThat(handler.getAnnotation(MessageExceptionHandler.class).value())
                .containsExactly(BusinessException.class);
        SendToUser sendToUser = handler.getAnnotation(SendToUser.class);
        assertThat(sendToUser.destinations()).containsExactly("/queue/chat-errors");
        assertThat(sendToUser.broadcast()).isFalse();
    }
}
