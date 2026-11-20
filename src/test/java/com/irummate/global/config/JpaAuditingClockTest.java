package com.irummate.global.config;

import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

// @CreatedDate/@LastModifiedDate가 시스템 시각이 아닌 주입된 Clock 기준으로 기록되는지 검증
@SpringBootTest(
        classes = JpaAuditingClockTest.PersistenceConfiguration.class,
        properties = {
                "spring.config.import=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.sql.init.mode=never",
                "spring.main.web-application-type=none"
        })
class JpaAuditingClockTest {

    // 2026-03-02 10:30 (KST)
    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-02T01:30:00Z");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 2, 10, 30);

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.irummate.domain")
    @EnableJpaRepositories(basePackageClasses = MatchingConfigRepository.class)
    @Import(JpaAuditingConfig.class)
    static class PersistenceConfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Seoul"));
        }

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                    .withInitScript("db/test-init.sql");
        }
    }

    @Autowired
    private MatchingConfigRepository matchingConfigRepository;

    @Test
    void auditingTimestampsComeFromInjectedClock() {
        LocalDate today = LocalDate.of(2026, 3, 2);
        MatchingConfig saved = matchingConfigRepository.saveAndFlush(
                new MatchingConfig(today, today.plusDays(1), today.plusDays(7)));

        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID).orElseThrow().getUpdatedAt())
                .isEqualTo(FIXED_NOW);
    }
}
