package com.irummate.domain.survey.dto;

import com.irummate.domain.survey.entity.SurveyAnswerField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPreferencesRequestDto {

    @NotNull(message = "smokingStatus는 필수입니다.")
    @Min(value = 0, message = "smokingStatus는 0(비흡연) 또는 1(흡연)이어야 합니다.")
    @Max(value = 1, message = "smokingStatus는 0(비흡연) 또는 1(흡연)이어야 합니다.")
    private Integer smokingStatus;

    @Size(max = 500, message = "introduce는 500자 이하여야 합니다.")
    private String introduce;

    @Valid
    @NotNull(message = "answers는 필수입니다.")
    private SurveyAnswers answers;

    @NotNull(message = "visibleProfileFields는 필수입니다.")
    @Size(min = 1, max = 3, message = "visibleProfileFields는 1개 이상 3개 이하로 선택해야 합니다.")
    private List<@NotNull SurveyAnswerField> visibleProfileFields;
}
