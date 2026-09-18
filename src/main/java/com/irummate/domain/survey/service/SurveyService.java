package com.irummate.domain.survey.service;

import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.domain.survey.dto.SurveyAnswers;
import com.irummate.domain.survey.dto.UserPreferencesRequestDto;
import com.irummate.domain.survey.dto.UserPreferencesResponseDto;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.survey.util.UserPreferencesDtoMapper;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class SurveyService {

    final private UsersRepository usersRepository;
    final private UserPreferencesRepository userPreferencesRepository;
    final private MatchingConfigRepository matchingConfigRepository;

    @Autowired
    public SurveyService(UserPreferencesRepository userPreferencesRepository,
                         UsersRepository usersRepository,
                         MatchingConfigRepository matchingConfigRepository){
        this.userPreferencesRepository = userPreferencesRepository;
        this.usersRepository = usersRepository;
        this.matchingConfigRepository = matchingConfigRepository;
    }

    @Transactional(readOnly = true)
    public UserPreferencesResponseDto getSurveyStatus(Long userId){

        UserPreferences userPreferences = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.SURVEY_NOT_FOUND));


        return UserPreferencesDtoMapper.toDto(userPreferences);
    }

    @Transactional
    public void saveSurveyStatus(Long userId, UserPreferencesRequestDto requestDto){

        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(userPreferencesRepository.findByUserId(userId).isPresent()) {
            throw new BusinessException(ErrorCode.SURVEY_ALREADY_SUBMITTED);
        }

        validateVisibleProfileFields(requestDto);
        SurveyAnswers surveyAnswers = toSurveyAnswers(requestDto);

        userPreferencesRepository.save(UserPreferences.builder()
                        .introduce(requestDto.getIntroduce())
                        .answers(surveyAnswers)
                        .smokingStatus(requestDto.getSmokingStatus())
                        .user(user)
                        .isCompleted(true)
                        .isMatched(false)
                        .visibleProfileFields(requestDto.getVisibleProfileFields())
                        .build());
    }

    @Transactional
    public UserPreferencesResponseDto updateSurveyStatus(Long userId, UserPreferencesRequestDto requestDto){

        UserPreferences userPreferences = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.SURVEY_NOT_FOUND));

        validateSurveyEditable(userPreferences);
        validateVisibleProfileFields(requestDto);

        SurveyAnswers surveyAnswers = toSurveyAnswers(requestDto);

        userPreferences.updatePreferences(
                requestDto.getSmokingStatus(),
                requestDto.getIntroduce(),
                surveyAnswers,
                requestDto.getVisibleProfileFields()
        );

        userPreferencesRepository.saveAndFlush(userPreferences);

        return UserPreferencesDtoMapper.toDto(userPreferences);
    }

    private void validateSurveyEditable(UserPreferences userPreferences) {
        if (Boolean.TRUE.equals(userPreferences.getIsMatched())) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

        MatchingConfig matchingConfig = matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID)
                .orElseThrow(()->new BusinessException(ErrorCode.MATCH_DATE_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if (!today.isBefore(matchingConfig.getMatchStartDate())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "매칭 시작일 전까지만 설문을 수정할 수 있습니다.");
        }
    }

    private void validateVisibleProfileFields(UserPreferencesRequestDto requestDto) {
        int size = requestDto.getVisibleProfileFields().size();
        long distinctSize = requestDto.getVisibleProfileFields().stream()
                .distinct()
                .count();

        if (size != distinctSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private SurveyAnswers toSurveyAnswers(UserPreferencesRequestDto requestDto) {
        return SurveyAnswers.builder()
                .bedtime(requestDto.getAnswers().getBedtime())
                .snoring(requestDto.getAnswers().getSnoring())
                .sleepTalking(requestDto.getAnswers().getSleepTalking())
                .organizingStyle(requestDto.getAnswers().getOrganizingStyle())
                .eatingInRoom(requestDto.getAnswers().getEatingInRoom())
                .temperaturePreference(requestDto.getAnswers().getTemperaturePreference())
                .showerFrequency(requestDto.getAnswers().getShowerFrequency())
                .speakerStyle(requestDto.getAnswers().getSpeakerStyle())
                .callInRoom(requestDto.getAnswers().getCallInRoom())
                .build();
    }

}
