package com.irummate.domain.calendar.service;

import com.irummate.domain.calendar.dto.CalendarExtractionOutput.Candidate;
import com.irummate.domain.calendar.entity.CalendarEvent.Details;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.irummate.domain.calendar.service.CalendarEventService.SEOUL;

final class CalendarCandidateValidator {
    private static final Pattern DATES = Pattern.compile(
            "(?<!\\d)(?:(\\d{4})년\\s*)?(?:(\\d{1,2})월\\s*)?(\\d{1,2})일(?!\\s*(?:전|후|이내|동안|경과))"
            + "|(?<!\\d)(\\d{4})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?!\\d)"
            + "|(?<![\\d.])(\\d{1,2})\\s*\\.\\s*(\\d{1,2})\\s*\\.(?!\\d)");
    private static final Pattern TIMES = Pattern.compile(
            "(?<!\\d)(오전|오후)?\\s*(\\d{1,2})(?::(\\d{2})|시(?:\\s*(\\d{1,2})분)?)");
    private static final Pattern RANGE = Pattern.compile("[~～〜–—-]|부터");
    private static final Pattern OPEN_RANGE = Pattern.compile("[~～〜]|부터");

    private CalendarCandidateValidator() {}

    static Optional<Details> validate(Candidate candidate, ClaimedJob job) {
        if (candidate == null || candidate.title == null || candidate.title.isBlank()
                || candidate.title.length() > 200 || candidate.category == null || candidate.evidence == null
                || candidate.evidence.isBlank() || !job.bodyText().contains(candidate.evidence)
                || value(candidate.reviewReason) != null) {
            return Optional.empty();
        }
        try {
            return verifyDates(candidate, job.publishedAt());
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Details> verifyDates(Candidate candidate, LocalDate publishedAt) {
        var start = parseDate(value(candidate.startDate));
        var end = parseDate(value(candidate.endDate));
        var startTime = parseTime(value(candidate.startTime));
        var endTime = parseTime(value(candidate.endTime));
        String audience = value(candidate.audience);
        if (start == null || (end == null && endTime != null)
                || (end != null && ((startTime == null) != (endTime == null)))
                || (audience != null && audience.length() > 200)
                || !matchesEvidence(candidate.evidence, publishedAt, start, startTime, end, endTime)) {
            return Optional.empty();
        }

        boolean allDay = startTime == null;
        Instant startAt = allDay ? start.atStartOfDay(SEOUL).toInstant()
                : start.atTime(startTime).atZone(SEOUL).toInstant();
        Instant endAt = allDay ? (end == null ? start : end).plusDays(1).atStartOfDay(SEOUL).toInstant()
                : end == null ? null : end.atTime(endTime).atZone(SEOUL).toInstant();
        if (endAt != null && !endAt.isAfter(startAt)) {
            return Optional.empty();
        }

        return Optional.of(new Details(candidate.title.strip(), candidate.category, audience, null,
                allDay, startAt, endAt));
    }

    private static boolean matchesEvidence(String evidence, LocalDate publishedAt,
                                           LocalDate start, LocalTime startTime, LocalDate end, LocalTime endTime) {
        var dates = DATES.matcher(evidence);
        if (!dates.find()) {
            return false;
        }
        LocalDate evidenceStart = dateFromEvidence(dates, publishedAt, null);
        if (!start.equals(evidenceStart)) {
            return false;
        }
        int firstDateEnd = dates.end();
        if (!dates.find()) {
            String tail = evidence.substring(firstDateEnd);
            return end == null && !OPEN_RANGE.matcher(tail).find() && matchesTimes(tail, startTime);
        }
        return end != null
                && RANGE.matcher(evidence.substring(firstDateEnd, dates.start())).find()
                && matchesTimes(evidence.substring(firstDateEnd, dates.start()), startTime)
                && end.equals(dateFromEvidence(dates, publishedAt, evidenceStart))
                && matchesTimes(evidence.substring(dates.end()), endTime)
                && !dates.find();
    }

    private static LocalDate dateFromEvidence(java.util.regex.Matcher match, LocalDate publishedAt, LocalDate previous) {
        boolean numeric = match.group(4) != null;
        boolean shortNumeric = match.group(7) != null;
        String year = match.group(numeric ? 4 : 1);
        String month = match.group(numeric ? 5 : shortNumeric ? 7 : 2);
        int day = Integer.parseInt(match.group(numeric ? 6 : shortNumeric ? 8 : 3));
        if (month == null && previous == null) {
            return null;
        }

        int monthNumber = month == null ? previous.getMonthValue() : Integer.parseInt(month);
        if (year != null) {
            return LocalDate.of(Integer.parseInt(year), monthNumber, day);
        }
        if (previous != null) {
            int impliedYear = previous.getYear();
            if (previous.getMonthValue() == 12 && monthNumber == 1) {
                impliedYear++;
            }
            return LocalDate.of(impliedYear, monthNumber, day);
        }

        LocalDate nearest = null;
        long distance = 181;
        for (int candidateYear = publishedAt.getYear() - 1; candidateYear <= publishedAt.getYear() + 1; candidateYear++) {
            try {
                LocalDate date = LocalDate.of(candidateYear, monthNumber, day);
                long days = Math.abs(ChronoUnit.DAYS.between(publishedAt, date));
                if (days < distance) {
                    nearest = date;
                    distance = days;
                }
            } catch (DateTimeException ignored) {
                // A leap day may only be valid in one of the candidate years.
            }
        }
        return nearest;
    }

    private static boolean matchesTimes(String evidence, LocalTime expected) {
        var times = TIMES.matcher(evidence);
        var found = new ArrayList<LocalTime>();
        while (times.find()) {
            int hour = Integer.parseInt(times.group(2));
            if (times.group(1) != null) {
                if (hour < 1 || hour > 12) {
                    return false;
                }
                hour = hour % 12 + ("오후".equals(times.group(1)) ? 12 : 0);
            }
            String minute = times.group(3) == null ? times.group(4) : times.group(3);
            found.add(LocalTime.of(hour, minute == null ? 0 : Integer.parseInt(minute)));
        }
        return expected == null ? found.isEmpty() : found.size() == 1 && expected.equals(found.getFirst());
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        LocalDate date = LocalDate.parse(value);
        return date.getYear() >= 1 && date.getYear() <= 9999 ? date : null;
    }

    private static LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{2}:\\d{2}")) {
            throw new DateTimeException("Expected HH:mm");
        }
        return LocalTime.parse(value);
    }

    private static String value(Optional<String> value) {
        return value == null ? null : value.filter(s -> !s.isBlank()).orElse(null);
    }
}
