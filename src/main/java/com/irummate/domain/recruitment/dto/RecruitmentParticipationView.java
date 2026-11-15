package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.Recruitments;

import java.time.LocalDateTime;

// 로그인 사용자와 모집글의 관계를 나타내는 응답용 상태. DB의 역할과 참여 상태는 유지
public enum RecruitmentParticipationView {
    MINE,
    JOINED,
    LEFT,
    REMOVED,
    NONE;

    public static RecruitmentParticipationView from(boolean mine, RecruitmentParticipantStatus status) {
        if (mine) {
            return MINE;
        }
        if (status == null) {
            return NONE;
        }
        return switch (status) {
            case JOINED -> JOINED;
            case LEFT -> LEFT;
            case REMOVED -> REMOVED;
        };
    }

    // 목록과 상세에서 같은 기준으로 계산. 실제 참여 요청에서는 잠금 획득 후 다시 검증
    public boolean isJoinable(Recruitments recruitment, LocalDateTime now) {
        return this == NONE && recruitment.canAcceptParticipant(now);
    }
}
