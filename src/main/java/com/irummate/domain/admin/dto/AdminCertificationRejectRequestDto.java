package com.irummate.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCertificationRejectRequestDto {

    // 관리자가 심사 화면에서 확인한 인증 버전
    @NotNull(message = "확인한 인증 버전은 필수입니다.")
    @PositiveOrZero(message = "인증 버전은 0 이상이어야 합니다.")
    private Long expectedVersion;

    @NotBlank(message = "거절 사유는 필수입니다.")
    @Size(max = 500, message = "거절 사유는 500자를 초과할 수 없습니다.")
    private String adminComment;
}
