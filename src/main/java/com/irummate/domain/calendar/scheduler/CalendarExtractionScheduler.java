package com.irummate.domain.calendar.scheduler;

import com.irummate.domain.calendar.entity.CalendarExtractionJob.Status;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;
import com.irummate.domain.calendar.service.CalendarEventExtractionService;
import com.irummate.domain.calendar.service.CalendarExtractionResultService;
import com.irummate.domain.notice.event.NoticesIngested;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@ConditionalOnProperty(name = "calendar.extraction.enabled", havingValue = "true", matchIfMissing = true)
public class CalendarExtractionScheduler {
    private static final int MAX_ATTEMPTS = 3;

    private final CalendarExtractionJobRepository jobs;
    private final CalendarEventExtractionService extraction;
    private final CalendarExtractionResultService results;
    private final ThreadPoolTaskScheduler worker;
    private final AtomicBoolean drainQueued = new AtomicBoolean();

    public CalendarExtractionScheduler(CalendarExtractionJobRepository jobs,
                                       CalendarEventExtractionService extraction,
                                       CalendarExtractionResultService results,
                                       @Qualifier("calendarTaskScheduler") ThreadPoolTaskScheduler worker) {
        this.jobs = jobs;
        this.extraction = extraction;
        this.results = results;
        this.worker = worker;
    }

    @TransactionalEventListener(NoticesIngested.class)
    @Scheduled(fixedDelayString = "${calendar.extraction.recovery-interval-ms:3600000}",
            scheduler = "calendarTaskScheduler")
    public void requestDrain() {
        if (!drainQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.execute(this::drain);
        } catch (TaskRejectedException e) {
            drainQueued.set(false);
            log.warn("일정 추출 작업 제출 실패. 다음 수집 또는 복구 실행에서 재시도합니다.");
        }
    }

    private void drain() {
        // 실행 중에 온 요청은 후속 실행 한 건으로 합친다.
        drainQueued.set(false);
        while (!Thread.currentThread().isInterrupted()) {
            var job = jobs.claim(UUID.randomUUID());
            if (job.isEmpty()) {
                return;
            }
            processJob(job.get());
        }
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
