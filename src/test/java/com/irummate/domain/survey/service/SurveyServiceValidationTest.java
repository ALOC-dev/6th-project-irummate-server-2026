package com.irummate.domain.survey.service;

import com.irummate.domain.matching.dto.MatchingPeriod;
import com.irummate.domain.matching.service.MatchingPeriodProvider;
import com.irummate.domain.survey.dto.SurveyAnswers;
import com.irummate.domain.survey.dto.UserPreferencesRequestDto;
import com.irummate.domain.survey.entity.SurveyAnswerField;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SurveyServiceValidationTest {
    private final UsersRepository users = mock(UsersRepository.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final MatchingPeriodProvider periods = mock(MatchingPeriodProvider.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final SurveyService service = new SurveyService(users, preferences, periods, clock);

    @Test
    void checksForDuplicateSubmissionAfterLockingUser() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Users.class)));
        when(preferences.findByUserId(1L)).thenReturn(Optional.of(survey(true, false)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveSurveyStatus(1L, request()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SURVEY_ALREADY_SUBMITTED);
        var order = inOrder(users, preferences);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(preferences).findByUserId(1L);
        verify(preferences, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"read, missing", "read, incomplete", "update, missing", "update, incomplete"})
    void rejectsMissingOrIncompleteSurveyWithExistingError(String operation, String state) {
        Optional<UserPreferences> found = state.equals("missing")
                ? Optional.empty() : Optional.of(survey(false, false));
        // 조회는 일반 조회, 수정은 잠금 조회를 사용
        when(preferences.findByUserId(1L)).thenReturn(found);
        when(preferences.findByUserIdForUpdate(1L)).thenReturn(found);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            if (operation.equals("read")) service.getSurveyStatus(1L);
            else service.updateSurveyStatus(1L, request());
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SURVEY_REQUIRED);
        if (operation.equals("read")) verify(preferences).findByUserId(1L);
        else verify(preferences).findByUserIdForUpdate(1L);
        verify(preferences, never()).saveAndFlush(any());
        verifyNoInteractions(periods);
    }

    @Test
    void readsCompletedSurveyWithSingleLookup() {
        when(preferences.findByUserId(1L)).thenReturn(Optional.of(survey(true, false)));

        assertThat(service.getSurveyStatus(1L).getIsCompleted()).isTrue();

        verify(preferences).findByUserId(1L);
        verifyNoMoreInteractions(preferences);
    }

    @Test
    void updatesCompletedSurveyBeforeMatchStart() {
        UserPreferences saved = survey(true, false);
        when(preferences.findByUserIdForUpdate(1L)).thenReturn(Optional.of(saved));
        when(periods.getRequired()).thenReturn(period(LocalDate.now(clock).plusDays(1)));

        assertThat(service.updateSurveyStatus(1L, request()).getIntroduce()).isEqualTo("수정된 소개");

        verify(preferences).findByUserIdForUpdate(1L);
        verify(preferences).saveAndFlush(saved);
    }

    @Test
    void stillRejectsSurveyUpdateAfterMatchStart() {
        when(preferences.findByUserIdForUpdate(1L)).thenReturn(Optional.of(survey(true, false)));
        when(periods.getRequired()).thenReturn(period(LocalDate.now(clock)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateSurveyStatus(1L, request()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(preferences, never()).saveAndFlush(any());
    }

    @Test
    void stillRejectsSurveyUpdateAfterFinalConfirmation() {
        when(preferences.findByUserIdForUpdate(1L)).thenReturn(Optional.of(survey(true, true)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateSurveyStatus(1L, request()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CONFIRMED);
        verify(preferences, never()).saveAndFlush(any());
        verifyNoInteractions(periods);
    }

    private UserPreferences survey(boolean completed, boolean matched) {
        return UserPreferences.builder().userId(1L).answers(answers()).smokingStatus(0)
                .isCompleted(completed).isMatched(matched).build();
    }

    private UserPreferencesRequestDto request() {
        return UserPreferencesRequestDto.builder().smokingStatus(0).introduce("수정된 소개")
                .answers(answers()).visibleProfileFields(List.of(SurveyAnswerField.BEDTIME)).build();
    }

    private SurveyAnswers answers() {
        return SurveyAnswers.builder().bedtime(1).snoring(1).sleepTalking(1).organizingStyle(1)
                .temperaturePreference(1).showerFrequency(1).speakerStyle(1).callInRoom(1).eatingInRoom(1).build();
    }

    private MatchingPeriod period(LocalDate matchStart) {
        return new MatchingPeriod(matchStart.minusDays(7), matchStart, matchStart.plusDays(7));
    }
}
