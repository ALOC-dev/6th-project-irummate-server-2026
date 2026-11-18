package com.irummate.domain.recruitment.entity;

import com.irummate.domain.recruitment.dto.RecruitmentUpdateRequestDto;
import com.irummate.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentsUpdateTest {

    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private Recruitments recruitment(RecruitmentStatus status) {
        return Recruitments.builder()
                .title("기존 제목")
                .place("기존 장소")
                .activityAt(now.plusDays(2))
                .deadlineAt(now.plusDays(1))
                .nowParticipants(3)
                .maxParticipants(5)
                .category(RecruitmentCategory.GROUP_BUY)
                .priceType(RecruitmentPriceType.FIXED)
                .amount(1000)
                .status(status)
                .visibility(RecruitmentVisibility.VISIBLE)
                .build();
    }

    @Test
    void titleOnlyRequestPreservesOtherFields() {
        RecruitmentUpdateRequestDto request = mapper.readValue(
                "{\"title\":\"새 제목\"}", RecruitmentUpdateRequestDto.class);
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);

        recruitment.update(request.getTitle(), request.getPlace(),
                request.getActivityAt(),
                request.getMaxParticipants(), request.getPriceType(), request.getAmount(), now);

        assertTrue(request.isUpdateRequested());
        assertFalse(mapper.readValue("{}",
                RecruitmentUpdateRequestDto.class).isUpdateRequested());
        assertEquals("새 제목", recruitment.getTitle());
        assertEquals("기존 장소", recruitment.getPlace());
        assertEquals(now.plusDays(2), recruitment.getActivityAt());
        assertEquals(now.plusDays(1), recruitment.getDeadlineAt());
        assertEquals(5, recruitment.getMaxParticipants());
        assertEquals(1000, recruitment.getAmount());
        assertEquals(RecruitmentPriceType.FIXED, recruitment.getPriceType());
    }

    @Test
    void activityWithinOneHourOrInvalidCapacityDoNotChangeFields() {
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);

        assertThrows(BusinessException.class, () -> recruitment.update(
                "변경 제목", null, now.plusMinutes(59), null, null, null, now));
        assertThrows(BusinessException.class, () -> recruitment.update(
                "변경 제목", null, null, 2, null, null, now));

        assertEquals("기존 제목", recruitment.getTitle());
        assertEquals(5, recruitment.getMaxParticipants());
    }

    @Test
    void reducingCapacityToCurrentCountClosesRecruitment() {
        Recruitments recruitment = recruitment(RecruitmentStatus.OPEN);
        recruitment.update(null, null, null, 3, null, null, now);
        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
        assertEquals(now, recruitment.getClosedAt());
    }

    @Test
    void increasingCapacityDoesNotReopenClosedRecruitment() {
        Recruitments recruitment = recruitment(RecruitmentStatus.CLOSED);
        recruitment.update(null, null, null, 6, null, null, now);
        assertEquals(6, recruitment.getMaxParticipants());
        assertEquals(RecruitmentStatus.CLOSED, recruitment.getStatus());
    }
}
