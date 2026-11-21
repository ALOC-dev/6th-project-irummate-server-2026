package com.irummate.global.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SemesterUtils {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private SemesterUtils() {
    }

    // 주입된 Clock을 쓰지 않는 기존 호출부용(매칭/설문/게시판 외 도메인)
    public static String currentSemester() {
        return currentSemester(Clock.system(KST));
    }

    // 주입된 Clock 기준으로 현재 학기 계산
    public static String currentSemester(Clock clock) {
        LocalDate now = LocalDate.now(clock);
        int half = now.getMonthValue() <= 6 ? 1 : 2;
        return now.getYear() + "-" + half;
    }
}
