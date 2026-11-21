package com.irummate.domain.chatbot.repository;

public interface FaqSearchResult {
    Long getId();
    String getQuestion();
    String getAnswer();
    Double getDistance();
}
