package com.irummate.domain.survey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAnswers {

    @NotNull(message = "bedtime은 필수입니다.")
    @Min(value = 1, message = "bedtime은 1 이상이어야 합니다.")
    @Max(value = 5, message = "bedtime은 5 이하여야 합니다.")
    private Integer bedtime;

    @NotNull(message = "snoring은 필수입니다.")
    @Min(value = 1, message = "snoring은 1 이상이어야 합니다.")
    @Max(value = 5, message = "snoring은 5 이하여야 합니다.")
    private Integer snoring;

    @NotNull(message = "sleepTalking은 필수입니다.")
    @Min(value = 1, message = "sleepTalking은 1 이상이어야 합니다.")
    @Max(value = 5, message = "sleepTalking은 5 이하여야 합니다.")
    private Integer sleepTalking;

    @NotNull(message = "organizingStyle은 필수입니다.")
    @Min(value = 1, message = "organizingStyle은 1 이상이어야 합니다.")
    @Max(value = 5, message = "organizingStyle은 5 이하여야 합니다.")
    private Integer organizingStyle;

    @NotNull(message = "eatingInRoom은 필수입니다.")
    @Min(value = 1, message = "eatingInRoom은 1 이상이어야 합니다.")
    @Max(value = 3, message = "eatingInRoom은 3 이하여야 합니다.")
    private Integer eatingInRoom;

    @NotNull(message = "temperaturePreference는 필수입니다.")
    @Min(value = 1, message = "temperaturePreference는 1 이상이어야 합니다.")
    @Max(value = 3, message = "temperaturePreference는 3 이하여야 합니다.")
    private Integer temperaturePreference;

    @NotNull(message = "showerFrequency은 필수입니다.")
    @Min(value = 1, message = "showerFrequency은 1 이상이어야 합니다.")
    @Max(value = 4, message = "showerFrequency은 4 이하여야 합니다.")
    private Integer showerFrequency;

    @NotNull(message = "speakerStyle은 필수입니다.")
    @Min(value = 1, message = "speakerStyle은 1 이상이어야 합니다.")
    @Max(value = 3, message = "speakerStyle은 3 이하여야 합니다.")
    private Integer speakerStyle;

    @NotNull(message = "callInRoom은 필수입니다.")
    @Min(value = 1, message = "callInRoom은 1 이상이어야 합니다.")
    @Max(value = 3, message = "callInRoom은 3 이하여야 합니다.")
    private Integer callInRoom;
}
