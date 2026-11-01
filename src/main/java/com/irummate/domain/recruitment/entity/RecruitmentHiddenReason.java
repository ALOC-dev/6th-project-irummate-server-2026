package com.irummate.domain.recruitment.entity;

public enum RecruitmentHiddenReason {
    AUTHOR_BANNED,      // 게시글 작성자 제재
    AUTHOR_WITHDRAWN,   // 게시글 작성자 탈퇴
    REPORT_ACCEPTED,    // 신고
    ADMIN_ACTION        // 신고 무관 직접 조치
}
