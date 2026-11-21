package com.irummate.domain.chat.controller;

import com.irummate.domain.chat.dto.ChatMessagesResponseDto;
import com.irummate.domain.chat.dto.ChatReadRequestDto;
import com.irummate.domain.chat.dto.ChatReadResponseDto;
import com.irummate.domain.chat.dto.ChatReadResult;
import com.irummate.domain.chat.dto.ChatRoomsResponseDto;
import com.irummate.domain.chat.dto.ChatUnreadCountResponseDto;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.chat.service.RecruitmentChatDelivery;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RecruitmentChatDelivery recruitmentDelivery;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate,
                          RecruitmentChatDelivery recruitmentDelivery) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.recruitmentDelivery = recruitmentDelivery;
    }

    @GetMapping("/rooms")
    public ResponseEntity<GlobalApiResponse<ChatRoomsResponseDto>> getChatRooms(
            @AuthenticationPrincipal Long userId
    ) {
        ChatRoomsResponseDto responseDto = chatService.getChatRooms(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "채팅방 목록 조회 성공", responseDto)
        );
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<GlobalApiResponse<ChatMessagesResponseDto>> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        ChatMessagesResponseDto responseDto = chatService.getMessages(roomId, userId, cursor, size);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "채팅방 메시지 조회 성공", responseDto)
        );
    }

    @PatchMapping("/rooms/{roomId}/read")
    public ResponseEntity<GlobalApiResponse<ChatReadResponseDto>> markMessagesAsRead(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChatReadRequestDto request
    ) {
        ChatReadResult result = chatService.markMessagesAsRead(roomId, userId, request.lastReadMessageId());

        if (result.event() != null) {
            if (chatService.isRecruitmentRoom(roomId)) {
                recruitmentDelivery.publishRead(roomId, result.event());
            } else {
                messagingTemplate.convertAndSend("/topic/room/" + roomId, result.event());
            }
        }

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "메시지 읽음 처리 성공", result.response())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(GlobalApiResponse.error(
                ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), null));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<GlobalApiResponse<ChatUnreadCountResponseDto>> getTotalUnreadCount(
            @AuthenticationPrincipal Long userId
    ) {
        ChatUnreadCountResponseDto responseDto = chatService.getTotalUnreadCount(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "안 읽은 메시지 개수 조회 성공", responseDto)
        );
    }
}
