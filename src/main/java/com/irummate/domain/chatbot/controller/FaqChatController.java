package com.irummate.domain.chatbot.controller;

import com.irummate.domain.chatbot.dto.FaqChatRequest;
import com.irummate.domain.chatbot.dto.FaqChatResponse;
import com.irummate.domain.chatbot.service.FaqChatService;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class FaqChatController {
    private final FaqChatService faqChatService;

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GlobalApiResponse<FaqChatResponse>> ask(@Valid @RequestBody FaqChatRequest request) {
        String answer = faqChatService.chat(request.question());
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "FAQ 답변 조회 성공", new FaqChatResponse(answer)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalApiResponse<Void>> handleInvalidBody() {
        ErrorCode error = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(error.getHttpStatus())
                .body(GlobalApiResponse.error(error.getHttpStatus(), error.getMessage(), null));
    }
}
