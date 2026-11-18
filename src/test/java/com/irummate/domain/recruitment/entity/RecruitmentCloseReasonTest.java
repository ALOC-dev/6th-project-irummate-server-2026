package com.irummate.domain.recruitment.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentCloseReasonTest {
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);

    private Recruitments recruitment() {
        return Recruitments.builder()
                .status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(2).maxParticipants(3)
                .deadlineAt(now.plusHours(1)).activityAt(now.plusHours(2))
                .build();
    }

    @Test
    void fullRecruitmentReopensWhenParticipantLeaves() {
        Recruitments recruitment = recruitment();
        recruitment.increaseParticipants(now);
        assertEquals(RecruitmentCloseReason.FULL, recruitment.getCloseReason());
        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());

        recruitment.decreaseParticipants(now.plusMinutes(1));
        assertEquals(2, recruitment.getNowParticipants());
        assertEquals(RecruitmentStatus.OPEN, recruitment.getStatus());
        assertNull(recruitment.getClosedAt());
        assertNull(recruitment.getCloseReason());
    }

    @Test
    void manualCloseStaysClosedWhenParticipantLeaves() {
        Recruitments recruitment = recruitment();
        recruitment.close(now);
        recruitment.decreaseParticipants(now.plusMinutes(1));
        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        assertEquals(RecruitmentCloseReason.MANUAL, recruitment.getCloseReason());
        assertEquals(now, recruitment.getClosedAt());
    }

    @Test
    void fullRecruitmentDoesNotReopenAtOrAfterDeadline() {
        for (LocalDateTime time : new LocalDateTime[]{now.plusHours(1), now.plusHours(3)}) {
            Recruitments recruitment = recruitment();
            recruitment.increaseParticipants(now);
            recruitment.decreaseParticipants(time);
            assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        }
    }

    @Test
    void hiddenOrFinishedRecruitmentDoesNotReopen() {
        for (RecruitmentStatus status : new RecruitmentStatus[]{
                RecruitmentStatus.CLOSED, RecruitmentStatus.CANCELLED, RecruitmentStatus.ENDED}) {
            Recruitments recruitment = Recruitments.builder()
                    .status(status).closeReason(RecruitmentCloseReason.FULL)
                    .visibility(RecruitmentVisibility.HIDDEN)
                    .nowParticipants(3).maxParticipants(3)
                    .deadlineAt(now.plusHours(1)).activityAt(now.plusHours(2)).build();
            recruitment.decreaseParticipants(now);
            assertEquals(status, recruitment.getStatus());
        }
    }

    @Test
    void capacityReductionRecordsFullReason() {
        Recruitments recruitment = recruitment();
        recruitment.update(null, null, null, 2, null, null, now);
        assertEquals(RecruitmentCloseReason.FULL, recruitment.getCloseReason());
    }

    @Test
    void explicitReopenClearsManualCloseReason() {
        Recruitments recruitment = recruitment();
        recruitment.close(now);
        recruitment.reopen(now);
        assertEquals(RecruitmentStatus.OPEN, recruitment.getStatus());
        assertNull(recruitment.getClosedAt());
        assertNull(recruitment.getCloseReason());
    }

    @Test
    void increasingCapacityReopensFullRecruitment() {
        Recruitments recruitment = recruitment();
        recruitment.increaseParticipants(now);
        recruitment.update(null, null, null, 4, null, null, now);

        assertEquals(RecruitmentStatus.OPEN, recruitment.getStatus());
        assertEquals(3, recruitment.getNowParticipants());
        assertEquals(4, recruitment.getMaxParticipants());
        assertNull(recruitment.getClosedAt());
        assertNull(recruitment.getCloseReason());
    }

    @Test
    void increasingCapacityKeepsManualClose() {
        Recruitments recruitment = recruitment();
        recruitment.close(now);
        recruitment.update(null, null, null, 4, null, null, now);

        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        assertEquals(RecruitmentCloseReason.MANUAL, recruitment.getCloseReason());
        assertEquals(now, recruitment.getClosedAt());
    }

    @Test
    void increasingCapacityDoesNotReopenAfterDeadline() {
        Recruitments recruitment = recruitment();
        recruitment.increaseParticipants(now);
        recruitment.update(null, null, null, 4, null, null, now.plusHours(1));

        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        assertEquals(RecruitmentCloseReason.FULL, recruitment.getCloseReason());
    }

    @Test
    void extendingActivityAndCapacityKeepsDeadlineClose() {
        Recruitments recruitment = recruitment();
        recruitment.closeByDeadline(now.plusHours(1));
        recruitment.update(null, null, now.plusHours(3), 4,
                null, null, now.plusHours(1));

        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        assertEquals(RecruitmentCloseReason.DEADLINE, recruitment.getCloseReason());
        assertEquals(now.plusMinutes(150), recruitment.getDeadlineAt());
    }
}
