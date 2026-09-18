package com.irummate.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCertificationRejectRequestDto {

    @NotBlank(message = "거절 사유는 필수입니다.")
    private String adminComment;
}
