package com.irummate.domain.recruitment.entity;

import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentScheduleTest {
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 17, 0);

    @Test
    void exactlyOneHourIsAllowedAndDeadlineIsThirtyMinutesBeforeActivity() {
        Recruitments recruitment = recruitment();

        recruitment.configureSchedule(now.plusHours(1), now);

        assertEquals(now.plusHours(1), recruitment.getActivityAt());
        assertEquals(now.plusMinutes(30), recruitment.getDeadlineAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1800, 3599})
    void lessThanOneHourCannotBeConfigured(long seconds) {
        Recruitments recruitment = recruitment();
        LocalDateTime oldActivity = recruitment.getActivityAt();
        LocalDateTime oldDeadline = recruitment.getDeadlineAt();

        assertEquals(ErrorCode.INVALID_RECRUITMENT_PERIOD, assertThrows(BusinessException.class,
                () -> recruitment.configureSchedule(now.plusSeconds(seconds), now)).getErrorCode());
        assertEquals(oldActivity, recruitment.getActivityAt());
        assertEquals(oldDeadline, recruitment.getDeadlineAt());
    }

    @Test
    void evenOneNanosecondBeforeMinimumIsRejected() {
        assertThrows(BusinessException.class,
                () -> recruitment().configureSchedule(now.plusHours(1).minusNanos(1), now));
    }

    @Test
    void changingActivityRecalculatesDeadlineAndRejectsInsufficientLeadTimeAtomically() {
        Recruitments recruitment = recruitment();
        recruitment.update(null, null, now.plusHours(1), null, null, null, now);
        assertEquals(now.plusMinutes(30), recruitment.getDeadlineAt());

        assertThrows(BusinessException.class, () -> recruitment.update(
                "변경되면 안 됨", null, now.plusMinutes(59), null, null, null, now));
        assertEquals("모집글", recruitment.getTitle());
        assertEquals(now.plusHours(1), recruitment.getActivityAt());
        assertEquals(now.plusMinutes(30), recruitment.getDeadlineAt());
    }

    @Test
    void textOnlyOrIdenticalActivityRequestIsAllowedInsideOneHour() {
        Recruitments recruitment = recruitment();
        recruitment.configureSchedule(now.plusHours(1), now);
        LocalDateTime later = now.plusMinutes(40);
        recruitment.closeByDeadline(now.plusMinutes(30));

        recruitment.update("변경 제목", null, null, null, null, null, later);
        recruitment.update(null, "변경 장소", now.plusHours(1), null, null, null, later);

        assertEquals("변경 제목", recruitment.getTitle());
        assertEquals("변경 장소", recruitment.getPlace());
        assertEquals(now.plusMinutes(30), recruitment.getDeadlineAt());
    }

    private Recruitments recruitment() {
        return Recruitments.builder().title("모집글").category(RecruitmentCategory.MEAL)
                .status(RecruitmentStatus.OPEN).visibility(RecruitmentVisibility.VISIBLE)
                .activityAt(now.plusDays(1)).deadlineAt(now.plusDays(1).minusMinutes(30))
                .nowParticipants(1).maxParticipants(3).build();
    }
}
