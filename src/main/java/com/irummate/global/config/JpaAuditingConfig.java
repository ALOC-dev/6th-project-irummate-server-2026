package com.irummate.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @CreatedDate, @LastModifiedDate 값을 주입된 Clock 기준으로 기록
// (@EntityListeners(AuditingEntityListener.class)를 붙인 엔티티에만 적용)
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "clockDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider clockDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
