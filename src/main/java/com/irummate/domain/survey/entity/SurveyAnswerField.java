package com.irummate.domain.survey.entity;

import com.irummate.domain.survey.dto.SurveyAnswers;

public enum SurveyAnswerField {
    BEDTIME,
    SNORING,
    SLEEP_TALKING,
    ORGANIZING_STYLE,
    TEMPERATURE_PREFERENCE,
    SHOWER_FREQUENCY,
    SPEAKER_STYLE,
    CALL_IN_ROOM,
    EATING_IN_ROOM;

    public Integer getValueFrom(SurveyAnswers answers) {
        return switch (this) {
            case BEDTIME -> answers.getBedtime();
            case SNORING -> answers.getSnoring();
            case SLEEP_TALKING -> answers.getSleepTalking();
            case ORGANIZING_STYLE -> answers.getOrganizingStyle();
            case TEMPERATURE_PREFERENCE -> answers.getTemperaturePreference();
            case SHOWER_FREQUENCY -> answers.getShowerFrequency();
            case SPEAKER_STYLE -> answers.getSpeakerStyle();
            case CALL_IN_ROOM -> answers.getCallInRoom();
            case EATING_IN_ROOM -> answers.getEatingInRoom();
        };
    }
}
