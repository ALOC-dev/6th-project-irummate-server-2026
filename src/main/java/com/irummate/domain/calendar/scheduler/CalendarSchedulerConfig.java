package com.irummate.domain.calendar.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "calendar.extraction.enabled", havingValue = "true", matchIfMissing = true)
public class CalendarSchedulerConfig {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler calendarTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("calendar-extraction-");
        return scheduler;
    }
}
