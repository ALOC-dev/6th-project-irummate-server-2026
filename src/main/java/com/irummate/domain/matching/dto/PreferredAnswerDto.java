package com.irummate.domain.matching.dto;

import com.irummate.domain.survey.entity.SurveyAnswerField;
import lombok.*;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreferredAnswerDto {
    private SurveyAnswerField field;
    private Integer value;
}
