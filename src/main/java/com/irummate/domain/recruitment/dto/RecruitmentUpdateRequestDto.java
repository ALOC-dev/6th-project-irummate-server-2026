package com.irummate.domain.recruitment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentUpdateRequestDto {

    // 게시글 제목
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "제목은 공백으로만 구성할 수 없습니다.")
    private String title;

    // 모집 설명 및 참여 조건
    @Size(max = 80, message = "내용은 80자 이하여야 합니다.")
    private String content;

    // 활동 또는 만남 장소
    @Size(max = 255, message = "장소는 255자 이하여야 합니다.")
    private String place;

    // 실제 활동 일시
    @Future(message = "활동 일시는 현재 시각 이후여야 합니다.")
    private LocalDateTime activityAt;

    // 모집 마감 일시
    @Future(message = "모집 마감 일시는 현재 시각 이후여야 합니다.")
    private LocalDateTime deadlineAt;

    // 작성자를 포함한 최대 인원
    @Min(value = 2, message = "최대 모집 인원은 2명 이상이어야 합니다.")
    private Integer maxParticipants;

    // 예상 비용
    @PositiveOrZero(message = "예상 비용은 0원 이상이어야 합니다.")
    private Integer estimatedFee;
}
