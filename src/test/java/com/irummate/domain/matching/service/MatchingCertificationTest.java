package com.irummate.domain.matching.service;

import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingCertificationTest {
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

    private MatchRequests pair;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingService, "entityManager", entityManager);
        when(entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'"))
                .thenReturn(timeoutQuery);

        Users me = Users.builder().oauthId("me").nickname("me").build();
        Users receiver = Users.builder().oauthId("receiver").nickname("receiver").build();
        ReflectionTestUtils.setField(me, "id", 1L);
        ReflectionTestUtils.setField(receiver, "id", 2L);
        pair = MatchRequests.builder().id(10L).userLow(me).userHigh(receiver)
                .userLowStatus(MatchStatus.RECOMMENDED).userHighStatus(MatchStatus.NONE).build();
        when(matchRepository.findByIds(1L, 2L)).thenReturn(Optional.of(pair));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void heartAndConfirmRejectReceiverWithoutValidCertification(boolean confirm) {
        prepareAction(confirm);
        MatchStatus before = pair.getStatusOf(1L);
        when(certificationRepository.existsApprovedCertification(
                eq(2L), eq(SemesterUtils.currentSemester()), any(LocalDateTime.class))).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            if (confirm) matchingService.confirm(1L, 2L);
            else matchingService.heart(1L, 2L);
        });

        assertThat(exception.getErrorCode()).isEqualTo(confirm
                ? ErrorCode.NOT_CONFIRMABLE_STATUS : ErrorCode.NOT_HEARTABLE_STATUS);
        assertThat(pair.getStatusOf(1L)).isEqualTo(before);
        verifyNoInteractions(chatService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void heartAndConfirmAllowReceiverWithValidCertification(boolean confirm) {
        prepareAction(confirm);
        when(certificationRepository.existsApprovedCertification(
                eq(2L), eq(SemesterUtils.currentSemester()), any(LocalDateTime.class))).thenReturn(true);

        if (confirm) matchingService.confirm(1L, 2L);
        else matchingService.heart(1L, 2L);

        assertThat(pair.getStatusOf(1L)).isEqualTo(confirm ? MatchStatus.FINAL_CONFIRMED : MatchStatus.HEART);
    }

    @Test
    void rejectDoesNotRequireReceiversCertification() {
        matchingService.reject(1L, 2L);

        assertThat(pair.getStatusOf(1L)).isEqualTo(MatchStatus.REJECTED);
        verifyNoInteractions(certificationRepository);
        verify(chatService).closeMatchRoomByMatchRequestId(10L);
    }

    private void prepareAction(boolean confirm) {
        if (confirm) {
            pair.updateStatusOf(1L, MatchStatus.HEART);
            pair.updateStatusOf(2L, MatchStatus.HEART);
            when(userPreferencesRepository.findAllByIdsForUpdate(List.of(1L, 2L)))
                    .thenReturn(List.of(
                            UserPreferences.builder().userId(1L).isCompleted(true).build(),
                            UserPreferences.builder().userId(2L).isCompleted(true).build()));
        }
    }
}
