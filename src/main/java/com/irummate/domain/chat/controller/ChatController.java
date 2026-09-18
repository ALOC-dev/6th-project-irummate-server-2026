package com.irummate.domain.chat.controller;

import com.irummate.domain.chat.dto.ChatMessagesResponseDto;
import com.irummate.domain.chat.dto.ChatReadResponseDto;
import com.irummate.domain.chat.dto.ChatRoomsResponseDto;
import com.irummate.domain.chat.dto.ChatUnreadCountResponseDto;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.global.aop.RequiresAuth;
import com.irummate.global.aop.RequiresCertification;
import com.irummate.global.response.GlobalApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @RequiresCertification
    @GetMapping("/rooms")
    public ResponseEntity<GlobalApiResponse<ChatRoomsResponseDto>> getChatRooms(
            @AuthenticationPrincipal Long userId
    ) {
        ChatRoomsResponseDto responseDto = chatService.getChatRooms(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "채팅방 목록 조회 성공", responseDto)
        );
    }

    @RequiresCertification
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

    @RequiresCertification
    @PatchMapping("/rooms/{roomId}/read")
    public ResponseEntity<GlobalApiResponse<ChatReadResponseDto>> markMessagesAsRead(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId
    ) {
        ChatReadResponseDto responseDto = chatService.markMessagesAsRead(roomId, userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "메시지 읽음 처리 성공", responseDto)
        );
    }

    @RequiresCertification
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