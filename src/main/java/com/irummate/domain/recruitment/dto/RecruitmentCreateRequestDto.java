package com.irummate.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentPriceType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Getter
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@JsonIgnoreProperties("deadlineAt")
public class RecruitmentCreateRequestDto {

    // 게시글 카테고리
    @NotNull(message = "카테고리 설정은 필수입니다.")
    private RecruitmentCategory category;

    // 게시글 제목
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    // 활동 또는 만남 장소
    @Size(max = 255, message = "장소는 255자 이하여야 합니다.")
    private String place;

    // 활동까지 최소 1시간 필요. 모집 마감은 서버에서 활동 30분 전으로 계산
    @NotNull(message = "활동 일시는 필수입니다.")
    @Future(message = "활동 일시는 현재 시각 이후여야 합니다.")
    private LocalDateTime activityAt;

    // 작성자를 제외하고 모집할 인원
    @NotNull(message = "최대 모집 인원은 필수입니다.")
    @Min(value = 1, message = "작성자를 제외한 모집 인원은 1명 이상이어야 합니다.")
    private Integer maxParticipants;

    // 공동구매 시 필수: FIXED는 1인 금액, SPLIT은 총액
    private RecruitmentPriceType priceType;

    @Positive(message = "금액은 0원보다 커야 합니다.")
    private Integer amount;

    // JSON 입력의 앞뒤 공백을 먼저 제거해 정리된 값으로 길이와 필수 여부 검증
    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
    }

    public void setPlace(String place) {
        this.place = place == null ? null : place.strip();
    }

}
