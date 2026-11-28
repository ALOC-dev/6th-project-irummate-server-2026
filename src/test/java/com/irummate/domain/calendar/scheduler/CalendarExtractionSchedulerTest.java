package com.irummate.domain.calendar.scheduler;

import com.irummate.domain.calendar.dto.CalendarExtractionOutput;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository;
import com.irummate.domain.calendar.repository.CalendarExtractionJobRepository.ClaimedJob;
import com.irummate.domain.calendar.service.CalendarEventExtractionService;
import com.irummate.domain.calendar.service.CalendarExtractionResultService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarExtractionSchedulerTest {
    private final CalendarExtractionJobRepository jobs = mock(CalendarExtractionJobRepository.class);
    private final CalendarEventExtractionService extraction = mock(CalendarEventExtractionService.class);
    private final CalendarExtractionResultService results = mock(CalendarExtractionResultService.class);
    private final ThreadPoolTaskScheduler worker = mock(ThreadPoolTaskScheduler.class);
    private final CalendarExtractionScheduler scheduler = new CalendarExtractionScheduler(jobs, extraction, results, worker);

    @Test
    void queuesOneWakeupAndDrainsAllReadyJobsOffTheCallerThread() {
        var first = job(1);
        var second = job(2);
        var output = new CalendarExtractionOutput();
        when(jobs.claim(any(UUID.class))).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(extraction.extract(first)).thenReturn(output);
        when(extraction.extract(second)).thenReturn(output);

        scheduler.requestDrain();
        scheduler.requestDrain();

        verifyNoInteractions(jobs, extraction);
        var drain = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).execute(drain.capture());

        drain.getValue().run();

        verify(results).complete(first, output);
        verify(results).complete(second, output);
        verify(jobs, times(3)).claim(any(UUID.class));
    }

    @Test
    void allowsAnotherWakeupWhileADrainIsRunning() {
        var first = job(1);
        var output = new CalendarExtractionOutput();
        when(jobs.claim(any(UUID.class))).thenReturn(Optional.of(first), Optional.empty());
        when(extraction.extract(first)).thenAnswer(call -> {
            scheduler.requestDrain();
            scheduler.requestDrain();
            return output;
        });

        scheduler.requestDrain();
        var drain = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).execute(drain.capture());
        drain.getValue().run();

        verify(worker, times(2)).execute(any(Runnable.class));
        verify(results).complete(first, output);
    }

    private static ClaimedJob job(long id) {
        return new ClaimedJob(id, id, 1, 1, 1, "기숙사 공지", LocalDate.of(2026, 11, 29),
                "12월 1일 기숙사 신청", UUID.randomUUID());
    }
}
