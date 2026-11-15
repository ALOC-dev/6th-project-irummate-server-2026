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
public class RecruitmentParticipantRemoveRequestDto {

    // 제외할 사용자의 공개 ID(HashIds). 참여자 목록의 userId를 그대로 전달
    @NotBlank(message = "제외할 참여자를 지정해 주세요.")
    private String targetUserId;

    // 강제 제외 사유
    @NotBlank(message = "제외 사유를 입력해 주세요.")
    @Size(max = 100, message = "제외 사유는 100자 이내로 입력해 주세요.")
    private String removalReason;
}
