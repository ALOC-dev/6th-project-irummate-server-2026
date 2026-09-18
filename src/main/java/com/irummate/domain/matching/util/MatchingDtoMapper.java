package com.irummate.domain.matching.util;

import com.irummate.domain.matching.dto.MatchCardStatus;
import com.irummate.domain.matching.dto.MatchingResponseDto;
import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;

public class MatchingDtoMapper {
    public static MatchingResponseDto toResponseDto(MatchRequests matchRequests) {
        return new MatchingResponseDto();
    }

    public static MatchCardStatus toCardStatus(MatchStatus myStatus, MatchStatus otherStatus) {
        if (myStatus == MatchStatus.FINAL_CONFIRMED
                && otherStatus == MatchStatus.FINAL_CONFIRMED) {
            return MatchCardStatus.FINAL_CONFIRMED;
        }

        if (myStatus == MatchStatus.CLOSED || otherStatus == MatchStatus.CLOSED) {
            return MatchCardStatus.CLOSED;
        }

        if (myStatus == MatchStatus.FINAL_CONFIRMED
                || otherStatus == MatchStatus.FINAL_CONFIRMED) {
            return MatchCardStatus.CONFIRM_PENDING;
        }

        if (otherStatus == MatchStatus.REJECTED) {
            return MatchCardStatus.REJECTED_BY_OTHER;
        }

        if (myStatus == MatchStatus.HEART && otherStatus == MatchStatus.HEART) {
            return MatchCardStatus.HEART_MATCHED;
        }

        if (myStatus == MatchStatus.HEART) {
            return MatchCardStatus.HEART_SENT;
        }

        if (otherStatus == MatchStatus.HEART) {
            return MatchCardStatus.HEART_RECEIVED;
        }

        return MatchCardStatus.RECOMMENDED;
    }

}
