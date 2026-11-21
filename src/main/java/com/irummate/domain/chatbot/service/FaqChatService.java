package com.irummate.domain.chatbot.service;

import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.openai.errors.OpenAIException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaqChatService {
    public static final int MAX_QUESTION_LENGTH = 500;
    public static final String FALLBACK_MESSAGE = """
            해당 질문은 현재 생활관 FAQ에서 확인할 수 없는 내용입니다.
            정확한 안내가 필요한 경우 생활관 홈페이지 또는 행정실을 이용해 주세요.""";

    private final FaqSearchService faqSearchService;
    private final FaqSelectionService faqSelectionService;
    private final FaqAnswerService faqAnswerService;

    public String chat(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "질문을 입력해 주세요.");
        }
        if (userQuestion.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "질문은 " + MAX_QUESTION_LENGTH + "자를 초과할 수 없습니다.");
        }

        try {
            var candidates = faqSearchService.search(userQuestion);
            if (candidates.isEmpty()) {
                return FALLBACK_MESSAGE;
            }
            return faqSelectionService.select(userQuestion, candidates)
                    .map(faq -> faqAnswerService.generateAnswer(userQuestion, faq))
                    .orElse(FALLBACK_MESSAGE);
        } catch (OpenAIException exception) {
            throw new IllegalStateException("OpenAI FAQ search or selection failed", exception);
        }
    }
}
