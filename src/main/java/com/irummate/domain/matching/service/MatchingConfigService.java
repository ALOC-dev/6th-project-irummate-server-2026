package com.irummate.domain.matching.service;

import com.irummate.domain.matching.dto.MatchingConfigDto;
import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MatchingConfigService {

    private final MatchingConfigRepository matchingConfigRepository;

    @Autowired
    public MatchingConfigService(MatchingConfigRepository matchingConfigRepository){
        this.matchingConfigRepository = matchingConfigRepository;
    }


    @Transactional
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

    @Transactional(readOnly = true)
    public MatchingConfigDto getMatchDate(){
        MatchingConfig config = matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID)
                .orElseThrow(()->new BusinessException(ErrorCode.MATCH_DATE_NOT_FOUND));

        MatchingConfigDto matchingConfigDto = new MatchingConfigDto();
        matchingConfigDto.setSurveyStartDate(config.getSurveyStartDate());
        matchingConfigDto.setMatchStartDate(config.getMatchStartDate());
        matchingConfigDto.setMatchEndDate(config.getMatchEndDate());

        return matchingConfigDto;
    }


}


