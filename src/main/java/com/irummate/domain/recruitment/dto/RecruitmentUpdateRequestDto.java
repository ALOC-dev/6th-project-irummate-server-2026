package com.irummate.domain.recruitment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.irummate.domain.recruitment.entity.RecruitmentPriceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class RecruitmentUpdateRequestDto {

    // 모든 필드는 선택값이며, 생략하거나 null로 전달하면 기존 값을 유지합니다.

    // 게시글 제목
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "제목은 공백으로만 구성할 수 없습니다.")
    private String title;

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

    // 생략하면 기존 방식과 금액 유지
    private RecruitmentPriceType priceType;

    @Positive(message = "금액은 0원보다 커야 합니다.")
    private Integer amount;

    // JSON 입력의 앞뒤 공백을 제거한 뒤 검증
    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
    }

    public void setPlace(String place) {
        // 빈 문자열은 장소를 비우는 요청이므로 null로 바꾸지 않고 유지
        this.place = place == null ? null : place.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "수정할 항목을 하나 이상 전달해야 합니다.")
    public boolean isUpdateRequested() {
        return title != null || place != null
                || activityAt != null || deadlineAt != null
                || maxParticipants != null || priceType != null || amount != null;
    }
}
