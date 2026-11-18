package com.irummate.domain.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentCancelRequestDto {

    // 활동 취소 사유
    @NotBlank(message = "취소 사유를 입력해 주세요.")
    @Size(max = 100, message = "취소 사유는 100자 이하여야 합니다.")
    private String cancelReason;

}
