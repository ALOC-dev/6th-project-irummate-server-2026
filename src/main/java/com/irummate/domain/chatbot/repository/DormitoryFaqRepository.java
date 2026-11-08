package com.irummate.domain.chatbot.repository;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DormitoryFaqRepository
        extends JpaRepository<DormitoryFaq, Long> {

    List<DormitoryFaq> findAllByIsActiveTrue();
}
