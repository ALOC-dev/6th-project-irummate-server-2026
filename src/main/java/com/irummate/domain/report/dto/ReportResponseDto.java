package com.irummate.domain.report.dto;

import com.irummate.domain.report.entity.ReportSourceType;

import java.time.LocalDateTime;

// 관리자 조회 응답이다. 신고 ID와 두 회원 ID는 Hashids로 변환한다.
public record ReportResponseDto(String reportId, String reporterId, String reporterNickname,
                                String reportedId, String reportedNickname,
                                ReportSourceType sourceType, String reason, LocalDateTime createdAt) { }
