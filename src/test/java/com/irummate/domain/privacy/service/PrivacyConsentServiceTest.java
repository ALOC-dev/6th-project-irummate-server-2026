package com.irummate.domain.privacy.service;

import com.irummate.domain.privacy.entity.ConsentChoice;
import com.irummate.domain.privacy.entity.ConsentHistory;
import com.irummate.domain.privacy.entity.PrivacyConsents;
import com.irummate.domain.privacy.repository.ConsentHistoryRepository;
import com.irummate.domain.privacy.repository.PrivacyConsentsRepository;

import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.irummate.domain.privacy.entity.ConsentChoice.Action.*;
import static com.irummate.domain.privacy.entity.ConsentChoice.Status.WITHDRAWN;
import static com.irummate.domain.privacy.entity.ConsentType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivacyConsentServiceTest {
    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-11-21T01:00:00Z");
    private final UsersRepository users = mock(UsersRepository.class);
    private final PrivacyConsentsRepository consents = mock(PrivacyConsentsRepository.class);
    private final ConsentHistoryRepository history = mock(ConsentHistoryRepository.class);
    private final PrivacyConsentService service =
            new PrivacyConsentService(users, consents, history, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void decliningAiDuringOnboardingStillAllowsProfileRegistration() {
        allowMember();

        service.submitOnboarding(USER_ID, true, "1.0", false, "1.0");

        var current = ArgumentCaptor.forClass(PrivacyConsents.class);
        verify(consents).save(current.capture());
        when(consents.findById(USER_ID)).thenReturn(Optional.of(current.getValue()));
        assertDoesNotThrow(() -> service.requireProfileConsent(USER_ID));
        assertEquals("DECLINED", service.findMyConsents(USER_ID).get(1).status());
        verify(history, times(2)).save(any(ConsentHistory.class));
    }

    @Test
    void mismatchedAiDocumentDoesNotWriteEitherChoice() {
        allowMember();

        var failure = assertThrows(BusinessException.class,
                () -> service.submitOnboarding(USER_ID, true, "1.0", false, "2.0"));

        assertEquals(ErrorCode.CONSENT_VERSION_CONFLICT, failure.getErrorCode());
        verify(consents, never()).save(any());
        verifyNoInteractions(history);
    }

    @Test
    void replayingOnboardingDoesNotUndoLaterAiWithdrawal() {
        allowMember();
        var current = new PrivacyConsents(USER_ID);
        current.change(AI_CERT_TRAINING, new ConsentChoice(WITHDRAWN, "1.0", NOW));
        when(consents.findById(USER_ID)).thenReturn(Optional.of(current));
        when(history.findFirstByUserIdAndConsentTypeOrderByIdAsc(USER_ID, PROFILE_COLLECTION))
                .thenReturn(Optional.of(new ConsentHistory(USER_ID, PROFILE_COLLECTION, AGREE, "1.0", NOW)));
        when(history.findFirstByUserIdAndConsentTypeOrderByIdAsc(USER_ID, AI_CERT_TRAINING))
                .thenReturn(Optional.of(new ConsentHistory(USER_ID, AI_CERT_TRAINING, AGREE, "1.0", NOW)));

        service.submitOnboarding(USER_ID, true, "1.0", true, "1.0");

        assertEquals(WITHDRAWN, current.choice(AI_CERT_TRAINING).status());
        verify(consents, never()).save(any());
        verify(history, never()).save(any());
    }

    private void allowMember() {
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(mock(Users.class)));
    }
}
