package com.irummate.domain.survey.util;

import com.irummate.domain.survey.dto.UserPreferencesRequestDto;
import com.irummate.domain.survey.dto.UserPreferencesResponseDto;
import com.irummate.domain.survey.entity.UserPreferences;

public class UserPreferencesDtoMapper {

    public static UserPreferences toEntity(UserPreferencesRequestDto requestDto){
        return UserPreferences.builder()
                .smokingStatus(requestDto.getSmokingStatus())
                .introduce(requestDto.getIntroduce())
                .answers(requestDto.getAnswers())
                .visibleProfileFields(requestDto.getVisibleProfileFields())
                .build();
    }

    public static UserPreferencesResponseDto toDto(UserPreferences userPreferences) {
        return UserPreferencesResponseDto.builder()
                .userId(userPreferences.getUserId())
                .isCompleted(userPreferences.getIsCompleted())
                .smokingStatus(userPreferences.getSmokingStatus())
                .introduce(userPreferences.getIntroduce())
                .answers(userPreferences.getAnswers())
                .createdAt(userPreferences.getCreatedAt())
                .updatedAt(userPreferences.getUpdatedAt())
                .visibleProfileFields(userPreferences.getVisibleProfileFields())
                .build();
    }

}
