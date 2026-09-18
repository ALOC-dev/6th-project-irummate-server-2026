package com.irummate.domain.chat.controller;

import com.irummate.domain.chat.dto.ChatNotificationDto;
import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatMessageSendRequestDto;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.util.HashIdsUtils;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final HashIdsUtils hashIdsUtils;

    public ChatWebSocketController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate,
            HashIdsUtils hashIdsUtils
    ) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.hashIdsUtils = hashIdsUtils;
    }

    @MessageMapping("/chat/send")
    public void sendMessage(
            @Valid @Payload ChatMessageSendRequestDto requestDto,
            Principal principal
    ) {
        Long senderId = resolveSenderId(principal);
        Long roomId = requestDto.getRoomId();

        ChatMessageResponseDto responseDto = chatService.sendMessage(
                roomId,
                senderId,
                requestDto.getMessage()
        );

        Long partnerId = chatService.getPartnerId(roomId, senderId);
        String encodedPartnerId = hashIdsUtils.encode(partnerId);
        ChatNotificationDto notificationDto = chatService.createNotification(roomId, senderId, partnerId, responseDto);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, responseDto);
        messagingTemplate.convertAndSend("/queue/user/" + encodedPartnerId, notificationDto);
    }

    private Long resolveSenderId(Principal principal) {
        if (principal instanceof WebSocketPrincipal webSocketPrincipal) {
            return webSocketPrincipal.getUserId();
        }

        throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
    }
}
