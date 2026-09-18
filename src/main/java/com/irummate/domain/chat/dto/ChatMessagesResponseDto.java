package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
// 채팅방 내 메시지 조회 목적.(메시지 여러개 보여주기)
@Getter
@AllArgsConstructor
public class ChatMessagesResponseDto {

    private List<ChatMessageResponseDto> messages;
    private Boolean hasNext;
}