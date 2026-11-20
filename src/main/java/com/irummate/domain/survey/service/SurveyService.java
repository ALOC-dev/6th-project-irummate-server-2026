package com.irummate.domain.survey.service;

import com.irummate.domain.matching.service.MatchingPeriodProvider;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final UsersRepository usersRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final MatchingPeriodProvider matchingPeriodProvider;
    private final Clock clock;


    @Transactional(readOnly = true)
    public UserPreferencesResponseDto getSurveyStatus(Long userId){

        UserPreferences userPreferences = getCompletedSurvey(userId);


        return UserPreferencesDtoMapper.toDto(userPreferences);
    }

    @Transactional
    public void saveSurveyStatus(Long userId, UserPreferencesRequestDto requestDto){

        Users user = usersRepository.findByIdForUpdate(userId)
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

        // 행 잠금 후 isMatched를 검사해야 동시에 커밋된 최종 확정(is_matched=true)을 덮어쓰지 않음
        // 잠금 대기 없이 실패(NOWAIT)하면 PessimisticLockingFailureException → 409
        UserPreferences userPreferences = requireCompleted(
                userPreferencesRepository.findByUserIdForUpdate(userId));

        validateSurveyEditable(userPreferences);
        validateVisibleProfileFields(requestDto);

        SurveyAnswers surveyAnswers = toSurveyAnswers(requestDto);

        userPreferences.updatePreferences(
                requestDto.getSmokingStatus(),
                requestDto.getIntroduce(),
                surveyAnswers,
                requestDto.getVisibleProfileFields(),
                LocalDateTime.now(clock)
        );

        userPreferencesRepository.saveAndFlush(userPreferences);

        return UserPreferencesDtoMapper.toDto(userPreferences);
    }

    // 조회한 설문으로 완료 여부까지 확인
    private UserPreferences getCompletedSurvey(Long userId) {
        return requireCompleted(userPreferencesRepository.findByUserId(userId));
    }

    private UserPreferences requireCompleted(Optional<UserPreferences> found) {
        UserPreferences userPreferences = found
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_REQUIRED));

        if (!Boolean.TRUE.equals(userPreferences.getIsCompleted())) {
            throw new BusinessException(ErrorCode.SURVEY_REQUIRED);
        }
        return userPreferences;
    }

    private void validateSurveyEditable(UserPreferences userPreferences) {
        if (Boolean.TRUE.equals(userPreferences.getIsMatched())) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

        if (!matchingPeriodProvider.getRequired().isSurveyEditable(LocalDate.now(clock))) {
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
