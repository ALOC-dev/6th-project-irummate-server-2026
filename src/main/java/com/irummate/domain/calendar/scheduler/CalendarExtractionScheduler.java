package com.irummate.domain.calendar.scheduler;

import com.irummate.domain.calendar.entity.CalendarExtractionJob.Status;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;
import com.irummate.domain.calendar.service.CalendarEventExtractionService;
import com.irummate.domain.calendar.service.CalendarExtractionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "calendar.extraction.enabled", havingValue = "true", matchIfMissing = true)
public class CalendarExtractionScheduler {
    private static final int MAX_ATTEMPTS = 3;

    private final CalendarExtractionJobRepository jobs;
    private final CalendarEventExtractionService extraction;
    private final CalendarExtractionResultService results;

    @Scheduled(fixedDelayString = "${calendar.extraction.poll-delay-ms:5000}", scheduler = "calendarTaskScheduler")
    public void process() {
        jobs.claim(UUID.randomUUID()).ifPresent(this::processJob);
    }

    private void processJob(ClaimedJob job) {
        if (job.bodyVersion() != job.currentVersion()) {
            jobs.finish(job, Status.SUPERSEDED, null, null, 0);
            return;
        }
        if (job.attempts() > MAX_ATTEMPTS) {
            jobs.finish(job, Status.FAILED, null, "ATTEMPTS_EXHAUSTED", 0);
            return;
        }

        try {
            results.complete(job, extraction.extract(job));
        } catch (RuntimeException e) {
            log.warn("기숙사 일정 추출 실패: jobId={}, attempt={}, type={}",
                    job.id(), job.attempts(), e.getClass().getSimpleName());
            if (job.attempts() >= MAX_ATTEMPTS) {
                jobs.finish(job, Status.FAILED, null, "EXTRACTION_FAILED", 0);
            } else {
                jobs.finish(job, Status.PENDING, null, "EXTRACTION_FAILED", 30 * job.attempts());
            }
        }
    }
}
