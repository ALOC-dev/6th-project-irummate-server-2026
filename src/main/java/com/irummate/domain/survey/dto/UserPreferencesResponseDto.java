package com.irummate.domain.survey.dto;

import com.irummate.domain.survey.entity.SurveyAnswerField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesResponseDto {

    private Long userId;
    private Boolean isCompleted;
    private Integer smokingStatus;
    private String introduce;
    private SurveyAnswers answers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SurveyAnswerField> visibleProfileFields;
}
