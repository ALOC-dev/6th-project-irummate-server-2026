package com.irummate.domain.matching.service;

import com.irummate.domain.matching.dto.MatchingPeriod;
import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingPeriodProvider {

    public static final String CACHE_NAME = "matchingPeriod";
    public static final String CACHE_KEY = "'singleton'";

    private final MatchingConfigRepository matchingConfigRepository;

    // 설정이 없으면 예외가 발생하고, 예외는 캐시되지 않으므로 관리자가 설정하면 바로 반영됨
    @Cacheable(cacheNames = CACHE_NAME, key = CACHE_KEY)
    public MatchingPeriod getRequired() {
        return matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID)
                .map(MatchingPeriod::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_DATE_NOT_FOUND));
    }
}
