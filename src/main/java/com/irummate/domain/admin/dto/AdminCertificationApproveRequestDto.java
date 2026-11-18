package com.irummate.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCertificationApproveRequestDto {

    // 관리자가 심사 화면에서 확인한 인증 버전
    @NotNull(message = "확인한 인증 버전은 필수입니다.")
    @PositiveOrZero(message = "인증 버전은 0 이상이어야 합니다.")
    private Long expectedVersion;
}
