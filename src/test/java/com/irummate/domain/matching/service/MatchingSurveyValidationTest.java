package com.irummate.domain.matching.service;

import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingSurveyValidationTest {
    @Mock private MatchRepository matchRepository;
    @Mock private UserPreferencesRepository userPreferencesRepository;
    @Mock private CertificationRepository certificationRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private ChatService chatService;
    @Mock private HashIdsUtils hashIdsUtils;
    @Mock private EntityManager entityManager;
    @Mock private Query timeoutQuery;
    @Spy private Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));
    @InjectMocks private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingService, "entityManager", entityManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"heart", "reject", "contact"})
    void existingRelationshipOperationsValidateRelationshipWithoutSurveyLookup(String operation) {
        if (operation.equals("heart") || operation.equals("reject")) stubLockTimeout();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            switch (operation) {
                case "heart" -> matchingService.heart(1L, 2L);
                case "reject" -> matchingService.reject(1L, 2L);
                case "contact" -> matchingService.getConfirmedContact(1L, 2L);
                default -> throw new IllegalArgumentException(operation);
            }
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MATCH_REQUEST_NOT_FOUND);
        verifyNoInteractions(userPreferencesRepository, certificationRepository, chatService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void matchRejectsMissingOrIncompleteLockedSurvey(boolean exists) {
        stubLockTimeout();
        when(userPreferencesRepository.findByUserIdForUpdate(1L)).thenReturn(exists
                ? Optional.of(UserPreferences.builder().userId(1L).isCompleted(false).build())
                : Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> matchingService.match(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SURVEY_REQUIRED);
        verifyNoInteractions(matchRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void confirmRejectsMissingOrIncompleteOwnLockedSurvey(boolean exists) {
        stubLockTimeout();
        UserPreferences receiver = UserPreferences.builder().userId(2L).isCompleted(true).build();
        when(userPreferencesRepository.findAllByIdsForUpdate(List.of(1L, 2L))).thenReturn(exists
                ? List.of(UserPreferences.builder().userId(1L).isCompleted(false).build(), receiver)
                : List.of(receiver));

        BusinessException exception = assertThrows(BusinessException.class, () -> matchingService.confirm(1L, 2L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SURVEY_REQUIRED);
        verifyNoInteractions(matchRepository);
    }

    @Test
    void matchUsesCompletedLockedSurveyForFurtherValidation() {
        stubLockTimeout();
        when(userPreferencesRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(
                UserPreferences.builder().userId(1L).isCompleted(true).isMatched(true).build()));

        BusinessException exception = assertThrows(BusinessException.class, () -> matchingService.match(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CONFIRMED);
        verifyNoInteractions(matchRepository);
    }

    @Test
    void confirmStillRejectsMissingReceiverAfterOwnSurveyValidation() {
        stubLockTimeout();
        when(userPreferencesRepository.findAllByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(
                UserPreferences.builder().userId(1L).isCompleted(true).build()));

        BusinessException exception = assertThrows(BusinessException.class, () -> matchingService.confirm(1L, 2L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(matchRepository);
    }

    @Test
    void matchPreservesImmediateLockFailureResponse() {
        stubLockTimeout();
        when(userPreferencesRepository.findByUserIdForUpdate(1L))
                .thenThrow(new CannotAcquireLockException("already locked"));

        BusinessException exception = assertThrows(BusinessException.class, () -> matchingService.match(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MATCH_IN_PROGRESS);
        verifyNoInteractions(matchRepository);
    }

    @Test
    void statusReturnsEmptyListWithoutSurveyLookup() {
        assertThat(matchingService.getMatchingStatus(1L)).isEmpty();

        verifyNoInteractions(userPreferencesRepository);
        verify(matchRepository).findAllVisibleByUserId(eq(1L), any(), any());
    }

    private void stubLockTimeout() {
        when(entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'"))
                .thenReturn(timeoutQuery);
    }
}
