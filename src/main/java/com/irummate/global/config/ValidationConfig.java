package com.irummate.global.config;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    // @Future, @Past 등 날짜 검증의 기준 시계를 서울 시간대로 통일
    @Bean
    public ValidationConfigurationCustomizer validationConfigurationCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
