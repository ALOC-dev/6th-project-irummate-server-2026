package com.irummate.global.util;

import java.time.LocalDate;
import java.time.ZoneId;

public final class SemesterUtils {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private SemesterUtils() {
    }

    public static String currentSemester() {
        LocalDate now = LocalDate.now(KST);
        int half = now.getMonthValue() <= 6 ? 1 : 2;
        return now.getYear() + "-" + half;
    }
}
