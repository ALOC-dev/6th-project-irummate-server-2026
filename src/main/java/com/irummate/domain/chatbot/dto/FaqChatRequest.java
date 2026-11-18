package com.irummate.domain.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.irummate.domain.chatbot.service.FaqChatService.MAX_QUESTION_LENGTH;

public record FaqChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = MAX_QUESTION_LENGTH, message = "질문은 " + MAX_QUESTION_LENGTH + "자를 초과할 수 없습니다.")
        String question
) {
}
