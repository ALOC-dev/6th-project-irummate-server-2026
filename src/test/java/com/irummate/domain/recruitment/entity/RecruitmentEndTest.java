package com.irummate.domain.recruitment.entity;

import com.irummate.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentEndTest {
    private final LocalDateTime activityAt = LocalDateTime.of(2030, 1, 1, 12, 0);

    private Recruitments recruitment(RecruitmentStatus status) {
        return Recruitments.builder().status(status).activityAt(activityAt)
                .nowParticipants(3).maxParticipants(5).build();
    }

    @Test
    void activityStartEndsBoardWithoutChangingCount() {
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);
        recruitment.end(activityAt);
        assertEquals(RecruitmentStatus.ENDED, recruitment.getStatus());
        assertEquals(activityAt, recruitment.getEndedAt());
        assertEquals(activityAt, recruitment.getClosedAt());
        assertEquals(3, recruitment.getNowParticipants());
    }

    @Test
    void closedBoardKeepsOriginalCloseTime() {
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);
        recruitment.close(activityAt.minusHours(1));
        recruitment.end(activityAt.plusMinutes(1));
        assertEquals(activityAt.minusHours(1), recruitment.getClosedAt());
        assertEquals(activityAt.plusMinutes(1), recruitment.getEndedAt());
        assertEquals(3, recruitment.getNowParticipants());
    }

    @Test
    void cannotEndBeforeStartOrFromTerminalState() {
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);
        assertThrows(BusinessException.class, () -> recruitment.end(activityAt.minusNanos(1)));
        assertEquals(RecruitmentStatus.OPEN, recruitment.getStatus());
        for (RecruitmentStatus status : new RecruitmentStatus[]{
                RecruitmentStatus.CANCELLED, RecruitmentStatus.ENDED}) {
            assertThrows(BusinessException.class, () -> recruitment(status).end(activityAt));
        }
    }
}
