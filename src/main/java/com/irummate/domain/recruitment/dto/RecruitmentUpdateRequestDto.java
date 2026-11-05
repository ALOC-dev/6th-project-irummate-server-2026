package com.irummate.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    // 모든 필드는 선택값이며, 생략하거나 null로 전달하면 기존 값을 유지합니다.

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

    // 작성자를 제외하고 모집할 인원
    @Min(value = 1, message = "작성자를 제외한 모집 인원은 1명 이상이어야 합니다.")
    private Integer maxParticipants;

    // 예상 비용
    @PositiveOrZero(message = "예상 비용은 0원 이상이어야 합니다.")
    private Integer estimatedFee;

    @JsonIgnore
    @AssertTrue(message = "수정할 항목을 하나 이상 전달해야 합니다.")
    public boolean isUpdateRequested() {
        return title != null || content != null || place != null
                || activityAt != null || deadlineAt != null
                || maxParticipants != null || estimatedFee != null;
    }
}
