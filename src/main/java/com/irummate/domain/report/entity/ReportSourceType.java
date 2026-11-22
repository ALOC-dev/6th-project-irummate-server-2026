package com.irummate.domain.report.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

// 신고 버튼을 누른 화면 또는 기능을 구분한다.
public enum ReportSourceType {
    PROFILE,
    MATCHING,
    CHAT;

    // 숫자 순번이 enum으로 변환되는 것을 막고 명시한 코드값만 받는다.
    @JsonCreator
    public static ReportSourceType from(String value) {
        return valueOf(value);
    }
}
