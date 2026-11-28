package com.irummate.domain.calendar.service;

import com.irummate.domain.calendar.dto.CalendarExtractionOutput;
import com.irummate.domain.calendar.dto.CalendarExtractionOutput.Candidate;
import com.irummate.domain.calendar.entity.CalendarEvent.Category;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;
import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarCandidateValidatorTest {
    @Test
    void extractsTimedRangeWithImplicitEndYear() {
        String evidence = "기숙사비 납부기한 : 2026년 11월 16일(월) 17:00 ~ 11월 18일(수) 22:00까지";
        Candidate candidate = candidate(evidence, "2026-11-16", "17:00", "2026-11-18", "22:00");

        var details = CalendarCandidateValidator.validate(candidate, job(evidence)).orElseThrow();

        assertThat(details.allDay()).isFalse();
        assertThat(details.startAt()).isEqualTo(Instant.parse("2026-11-16T08:00:00Z"));
        assertThat(details.endAt()).isEqualTo(Instant.parse("2026-11-18T13:00:00Z"));
    }

    @Test
    void allDayRangeIncludesFinalDate() {
        String evidence = "신청 기간 11월 16일 ~ 18일";
        Candidate candidate = candidate(evidence, "2026-11-16", null, "2026-11-18", null);

        var details = CalendarCandidateValidator.validate(candidate, job(evidence)).orElseThrow();

        assertThat(details.allDay()).isTrue();
        assertThat(details.endAt()).isEqualTo(Instant.parse("2026-11-18T15:00:00Z"));
    }

    @Test
    void aTimePointDoesNotRequireAnEnd() {
        String evidence = "발표 2026.11.20. 오후 2시";
        Candidate candidate = candidate(evidence, "2026-11-20", "14:00", null, null);

        var details = CalendarCandidateValidator.validate(candidate, job(evidence)).orElseThrow();

        assertThat(details.startAt()).isEqualTo(Instant.parse("2026-11-20T05:00:00Z"));
        assertThat(details.endAt()).isNull();
    }

    @Test
    void wrongDeadlineOrEvidenceIsReviewed() {
        String evidence = "납부 기간 2026년 11월 16일 17:00 ~ 11월 18일 22:00";
        Candidate candidate = candidate(evidence, "2026-11-16", "17:00", "2026-11-18", "23:00");

        assertThat(CalendarCandidateValidator.validate(candidate, job(evidence))).isEmpty();
        candidate.endTime = Optional.of("22:00");
        assertThat(CalendarCandidateValidator.validate(candidate, job("다른 공지 본문"))).isEmpty();
    }

    @Test
    void currentSdkCanBuildTheStructuredOutputSchema() {
        ResponseCreateParams.builder().model("gpt-6-luna").input("공지 본문")
                .text(CalendarExtractionOutput.class).build();
    }

    private static Candidate candidate(String evidence, String startDate, String startTime,
                                       String endDate, String endTime) {
        var candidate = new Candidate();
        candidate.title = "기숙사 일정";
        candidate.category = Category.PAYMENT;
        candidate.evidence = evidence;
        candidate.startDate = Optional.of(startDate);
        candidate.startTime = Optional.ofNullable(startTime);
        candidate.endDate = Optional.ofNullable(endDate);
        candidate.endTime = Optional.ofNullable(endTime);
        return candidate;
    }

    private static ClaimedJob job(String body) {
        return new ClaimedJob(1, 1, 1, 1, 1, "기숙사 일정",
                LocalDate.of(2026, 11, 15), body, UUID.randomUUID());
    }
}
