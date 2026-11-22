package com.irummate.domain.report.dto;

import com.irummate.domain.report.entity.ReportSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 신고자 ID는 JWT에서, 신고 대상 ID는 URL에서 받는다.
public record ReportRequestDto(
        @NotNull(message = "신고 발생 위치를 선택해주세요.")
        ReportSourceType sourceType,

        @NotBlank(message = "신고 사유를 입력해주세요.")
        @Size(max = 2000, message = "신고 사유는 2000자 이하여야 합니다.")
        String reason
) { }
