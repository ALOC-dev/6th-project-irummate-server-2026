package com.irummate.domain.matching.service;

import com.irummate.domain.matching.dto.MatchingConfigDto;
import com.irummate.domain.matching.dto.MatchingPeriod;
import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MatchingConfigService {

    private final MatchingConfigRepository matchingConfigRepository;
    private final MatchingPeriodProvider matchingPeriodProvider;


    @Transactional
    @CacheEvict(cacheNames = MatchingPeriodProvider.CACHE_NAME, key = MatchingPeriodProvider.CACHE_KEY)
    public void setMatchDate(LocalDate surveyStartDate,
                             LocalDate matchStartDate,
                             LocalDate matchEndDate){

        if (surveyStartDate == null || matchStartDate == null || matchEndDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "설문 시작일과 매칭 시작일, 종료일은 필수입니다.");
        }

        if (matchStartDate.isAfter(matchEndDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매칭 시작일은 종료일보다 늦을 수 없습니다.");
        }

        if (surveyStartDate.isAfter(matchStartDate)){
            throw new BusinessException(ErrorCode.INVALID_INPUT, "설문 시작일은 매칭 시작일보다 늦을 수 없습니다.");
        }

        MatchingConfig config = matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID)
                .orElseGet(()->new MatchingConfig(surveyStartDate, matchStartDate, matchEndDate));

        config.updateSurveyStartDate(surveyStartDate);
        config.updateMatchStartDate(matchStartDate);
        config.updateMatchEndDate(matchEndDate);
        matchingConfigRepository.save(config);

    }



    public MatchingConfigDto getMatchDate(){
        MatchingPeriod matchingPeriod = matchingPeriodProvider.getRequired();

        MatchingConfigDto matchingConfigDto = new MatchingConfigDto();
        matchingConfigDto.setSurveyStartDate(matchingPeriod.surveyStartDate());
        matchingConfigDto.setMatchStartDate(matchingPeriod.matchStartDate());
        matchingConfigDto.setMatchEndDate(matchingPeriod.matchEndDate());

        return matchingConfigDto;
    }


}


