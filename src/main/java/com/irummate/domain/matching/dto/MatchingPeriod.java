package com.irummate.domain.matching.dto;

import com.irummate.domain.matching.entity.MatchingConfig;

import java.time.LocalDate;

public record MatchingPeriod(
        LocalDate surveyStartDate,
        LocalDate matchStartDate,
        LocalDate matchEndDate
) {

    public static MatchingPeriod from(MatchingConfig config) {
        return new MatchingPeriod(
                config.getSurveyStartDate(),
                config.getMatchStartDate(),
                config.getMatchEndDate()
        );
    }

    // 매칭 가능 기간: 매칭 시작일 ~ 매칭 종료일
    public boolean isMatchOpen(LocalDate today) {
        return !today.isBefore(matchStartDate) && !today.isAfter(matchEndDate);
    }

    // 설문 가능 기간: 설문 시작일 ~ 매칭 종료일
    public boolean isSurveyOpen(LocalDate today) {
        return !today.isBefore(surveyStartDate) && !today.isAfter(matchEndDate);
    }

    // 설문 수정 가능: 매칭 시작일 전까지
    public boolean isSurveyEditable(LocalDate today) {
        return today.isBefore(matchStartDate);
    }
}
