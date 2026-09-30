package com.irummate.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class AdminUserBanRequestDto {

    @NotBlank(message = "정지 사유는 필수입니다.")
    @Size(max = 500, message = "정지 사유는 500자를 초과할 수 없습니다.")
    private String reason;

    private LocalDateTime expiresAt;
}
