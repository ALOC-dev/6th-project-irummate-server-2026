package com.irummate.domain.chatbot.repository;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DormitoryFaqRepository
        extends JpaRepository<DormitoryFaq, Long> {

    List<DormitoryFaq> findAllByIsActiveTrue();

    @Query(value = """
            SELECT
                id,
                question,
                answer,
                embedding <-> CAST(:queryVector AS vector) AS distance
            FROM dormitory_faq
            WHERE is_active = true
              AND embedding IS NOT NULL
            ORDER BY embedding <-> CAST(:queryVector AS vector)
            LIMIT 3
            """, nativeQuery = true)
    List<FaqSearchResult> findTop3ByEmbedding(
            @Param("queryVector") String queryVector
    );
}