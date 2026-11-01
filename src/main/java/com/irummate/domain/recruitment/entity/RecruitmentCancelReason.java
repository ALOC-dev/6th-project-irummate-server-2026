package com.irummate.domain.recruitment.entity;

public enum RecruitmentCancelReason {
    HOST_CANCELLED,     // HOST 직접 취소
    AUTHOR_WITHDRAWN,   // 작성자 탈퇴
    AUTHOR_BANNED,      // 작성자 제재
    ADMIN_CANCELLED,    // ADMIN 직접 조치
    OTHER               // 기타 사유
}
