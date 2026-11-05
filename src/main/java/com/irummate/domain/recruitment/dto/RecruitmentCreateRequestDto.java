package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.user.entity.Users;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentCreateRequestDto {

    // 게시글 카테고리
    @NotNull(message = "카테고리 설정은 필수입니다.")
    private RecruitmentCategory category;

    // 게시글 제목
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    // 모집 설명 및 참여 조건
    @Size(max = 80, message = "내용은 80자 이하여야 합니다.")
    private String content;

    // 활동 또는 만남 장소
    @Size(max = 255, message = "장소는 255자 이하여야 합니다.")
    private String place;

    // 실제 활동 일시
    @NotNull(message = "활동 일시는 필수입니다.")
    @Future(message = "활동 일시는 현재 시각 이후여야 합니다.")
    private LocalDateTime activityAt;

    // 모집 마감 일시
    @NotNull(message = "모집 마감 일시는 필수입니다.")
    @Future(message = "모집 마감 일시는 현재 시각 이후여야 합니다.")
    private LocalDateTime deadlineAt;

    // 작성자를 제외하고 모집할 인원
    @NotNull(message = "최대 모집 인원은 필수입니다.")
    @Min(value = 1, message = "작성자를 제외한 모집 인원은 1명 이상이어야 합니다.")
    private Integer maxParticipants;

    // 예상 비용
    @PositiveOrZero(message = "예상 비용은 0원 이상이어야 합니다.")
    private Integer estimatedFee;

}
