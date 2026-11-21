package com.irummate.domain.chatbot.service;

import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.repository.FaqSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqSearchService {

    private final EmbeddingService embeddingService;
    private final DormitoryFaqRepository dormitoryFaqRepository;

    public List<FaqSearchResult> search(String question) {

        float[] embedding =
                embeddingService.embedToArray(question);

        String queryVector = Arrays.toString(embedding);

        return dormitoryFaqRepository.findTop3ByEmbedding(queryVector);
    }
}