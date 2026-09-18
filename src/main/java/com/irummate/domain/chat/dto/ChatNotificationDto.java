package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatNotificationDto {

    private Long roomId;
    private String senderId;
    private String senderName;
    private String senderProfileImageUrl;
    private String message;
    private LocalDateTime createdAt;
    private int unreadCount;
    private int totalUnreadCount;
}
