package com.irummate.domain.chat.controller;

import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatMessageSendRequestDto;
import com.irummate.domain.chat.service.ChatDelivery;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.websocket.WebSocketErrorResponseDto;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final ChatDelivery chatDelivery;

    public ChatWebSocketController(ChatService chatService, ChatDelivery chatDelivery) {
        this.chatService = chatService;
        this.chatDelivery = chatDelivery;
    }

    @MessageMapping("/chat/send")
    public void sendMessage(
            @Payload ChatMessageSendRequestDto requestDto,
            Principal principal
    ) {
        if (requestDto == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Long senderId = resolveSenderId(principal);
        Long roomId = requestDto.getRoomId();

        ChatMessageResponseDto responseDto = chatService.sendMessage(
                roomId,
                senderId,
                requestDto.getMessage()
        );

        chatDelivery.publishMessage(roomId, senderId, responseDto);
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = "/queue/chat-errors", broadcast = false)
    public WebSocketErrorResponseDto handleChatBusinessException(BusinessException exception) {
        return new WebSocketErrorResponseDto(
                exception.getErrorCode().name(),
                exception.getMessage()
        );
    }

    private Long resolveSenderId(Principal principal) {
        if (principal instanceof WebSocketPrincipal webSocketPrincipal) {
            return webSocketPrincipal.getUserId();
        }

        throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
    }
}
