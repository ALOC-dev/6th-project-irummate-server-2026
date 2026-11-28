package com.irummate.domain.calendar.service;

import com.irummate.domain.calendar.dto.CalendarExtractionOutput;
import com.irummate.domain.calendar.entity.CalendarEvent;
import com.irummate.domain.calendar.entity.CalendarEvent.Details;
import com.irummate.domain.calendar.entity.CalendarEvent.SourceType;
import com.irummate.domain.calendar.entity.CalendarExtractionJob.Status;
import com.irummate.domain.calendar.repository.CalendarEventRepository;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;
import com.irummate.domain.notice.repository.NoticeRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CalendarExtractionResultService {
    private final CalendarExtractionJobRepository jobs;
    private final CalendarEventRepository events;
    private final NoticeRepository notices;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional
    public void complete(ClaimedJob job, CalendarExtractionOutput output) {
        var notice = notices.lockById(job.noticeId()).orElseThrow();
        if (notice.getBodyVersion() != job.bodyVersion()) {
            jobs.finish(job, Status.SUPERSEDED, null, null, 0);
            return;
        }

        JsonNode result = json.valueToTree(output.schedules);
        String payload = json.writeValueAsString(result);
        var previous = jobs.findPreviousResult(job);
        if (previous.isPresent()) {
            var prior = previous.get();
            boolean needsReview = prior.status() == Status.REVIEW_REQUIRED || !result.equals(json.readTree(prior.result()));
            jobs.finish(job, needsReview ? Status.REVIEW_REQUIRED : Status.COMPLETED,
                    payload, needsReview ? "NOTICE_CHANGED" : null, 0);
            return;
        }

        var newEvents = new LinkedHashSet<Details>();
        for (var candidate : output.schedules) {
            var details = CalendarCandidateValidator.validate(candidate, job);
            if (details.isEmpty()) {
                jobs.finish(job, Status.REVIEW_REQUIRED, payload, "AMBIGUOUS_EVENT", 0);
                return;
            }
            if (hasDuplicate(details.get(), job.noticeId())) {
                jobs.finish(job, Status.REVIEW_REQUIRED, payload, "POSSIBLE_DUPLICATE", 0);
                return;
            }
            newEvents.add(details.get());
        }

        if (jobs.finish(job, Status.COMPLETED, payload, null, 0)) {
            var now = clock.instant();
            events.saveAll(newEvents.stream()
                    .map(details -> new CalendarEvent(SourceType.AI, notice, details, now)).toList());
        }
    }

    private boolean hasDuplicate(Details details, long noticeId) {
        return events.findDuplicateCandidates(details.category(), details.startAt(), noticeId).stream()
                .map(CalendarEvent::getDetails)
                .anyMatch(existing -> existing.title().equalsIgnoreCase(details.title())
                        && Objects.equals(existing.endAt(), details.endAt()));
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> findReviews(long afterId) {
        if (afterId < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "afterId는 0 이상이어야 합니다.");
        }
        return jobs.findReviews(afterId).stream().map(row -> new ReviewItem(
                row.id(), row.noticeId(), row.bodyVersion(), row.title(), row.url(),
                json.readTree(row.result()), row.reason())).toList();
    }

    @Transactional
    public void resolveReview(long jobId) {
        if (!jobs.resolveReview(jobId)) {
            throw new BusinessException(ErrorCode.CALENDAR_EXTRACTION_NOT_FOUND);
        }
    }

    public record ReviewItem(long id, long noticeId, int bodyVersion, String noticeTitle, String noticeUrl,
                             JsonNode schedules, String reason) {}
}
