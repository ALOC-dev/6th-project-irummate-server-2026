package com.irummate.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatRoomsResponseDto {

    private List<ChatRoomResponseDto> rooms;
}