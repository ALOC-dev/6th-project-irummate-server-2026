package com.irummate.domain.matching.dto;

public enum MatchCardStatus {
    RECOMMENDED,          // 나에게 추천된 상태
    HEART_SENT,           // 내가 하트를 보냄
    HEART_RECEIVED,       // 상대가 나에게 하트를 보냄
    HEART_MATCHED,        // 양방향 하트
    REJECTED_BY_OTHER,    // 상대방이 거절함
    CONFIRM_PENDING,      // 둘 중 한 명만 최종 확정함
    FINAL_CONFIRMED,      // 둘 다 최종 확정함
    CLOSED                // 나 또는 상대가 다른 사람과 확정되어 종료됨
}
