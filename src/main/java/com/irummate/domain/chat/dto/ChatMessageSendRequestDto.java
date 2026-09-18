package com.irummate.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageSendRequestDto {

    @NotNull(message = "채팅방 ID는 필수입니다.")
    private Long roomId;

    @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
    @Size(max = 500, message = "메시지는 500자를 초과할 수 없습니다.")
    private String message;
}
