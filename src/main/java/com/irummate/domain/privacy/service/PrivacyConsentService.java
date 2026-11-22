package com.irummate.domain.privacy.service;

import com.irummate.domain.privacy.dto.ConsentsResponse.ConsentView;
import com.irummate.domain.privacy.entity.ConsentChoice;
import com.irummate.domain.privacy.entity.ConsentHistory;
import com.irummate.domain.privacy.entity.ConsentType;
import com.irummate.domain.privacy.entity.PrivacyConsents;
import com.irummate.domain.privacy.repository.ConsentHistoryRepository;
import com.irummate.domain.privacy.repository.PrivacyConsentsRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.irummate.domain.privacy.entity.ConsentChoice.Action.*;
import static com.irummate.domain.privacy.entity.ConsentChoice.Status.AGREED;
import static com.irummate.domain.privacy.entity.ConsentType.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivacyConsentService {
    private static final String PROFILE_DOCUMENT_VERSION = "1.0";
    private static final String AI_DOCUMENT_VERSION = "1.0";

    private final UsersRepository users;
    private final PrivacyConsentsRepository consents;
    private final ConsentHistoryRepository history;
    private final Clock clock;

    public List<ConsentView> findMyConsents(Long userId) {
        var current = consents.findById(userId).orElseGet(() -> new PrivacyConsents(userId));
        return Arrays.stream(ConsentType.values()).map(type -> {
            var choice = current.choice(type);
            return new ConsentView(type, type == PROFILE_COLLECTION, choice.status().name(),
                    choice.documentVersion(), currentVersion(type), choice.updatedAt());
        }).toList();
    }

    @Transactional
    public void submitOnboarding(Long userId, boolean profileAgreed, String profileVersion,
                                 boolean aiAgreed, String aiVersion) {
        if (!profileAgreed) {
            throw new BusinessException(ErrorCode.PROFILE_CONSENT_REQUIRED);
        }
        lockMember(userId);
        var aiAction = aiAgreed ? AGREE : DECLINE;
        var existing = consents.findById(userId);
        if (existing.isPresent()) {
            if (matchesFirstChoice(userId, PROFILE_COLLECTION, AGREE, profileVersion)
                    && matchesFirstChoice(userId, AI_CERT_TRAINING, aiAction, aiVersion)) {
                return;
            }
            throw new BusinessException(ErrorCode.ONBOARDING_CONSENT_ALREADY_SUBMITTED);
        }

        validateVersion(PROFILE_COLLECTION, profileVersion);
        validateVersion(AI_CERT_TRAINING, aiVersion);
        var current = new PrivacyConsents(userId);
        Instant now = clock.instant();
        record(current, userId, PROFILE_COLLECTION, AGREE, profileVersion, now);
        record(current, userId, AI_CERT_TRAINING, aiAction, aiVersion, now);
        consents.save(current);
    }

    @Transactional
    public void changeAiConsent(Long userId, ConsentChoice.Action action, String version) {
        lockMember(userId);
        if (action == AGREE) {
            validateVersion(AI_CERT_TRAINING, version);
        }
        var current = consents.findById(userId).orElse(null);
        if (current == null) {
            if (action == WITHDRAW) {
                return;
            }
            throw new BusinessException(ErrorCode.PROFILE_CONSENT_REQUIRED);
        }
        var previous = current.choice(AI_CERT_TRAINING);
        if (action == WITHDRAW) {
            withdraw(current, userId, AI_CERT_TRAINING);
        } else if (previous.status() != AGREED || !version.equals(previous.documentVersion())) {
            record(current, userId, AI_CERT_TRAINING, AGREE, version, clock.instant());
        }
    }

    // Internal operation for staff after processing a verified withdrawal request.
    @Transactional
    public void recordProfileWithdrawal(Long userId) {
        lockMember(userId);
        consents.findById(userId).ifPresent(current -> withdraw(current, userId, PROFILE_COLLECTION));
    }

    public void requireProfileConsent(Long userId) {
        boolean agreed = consents.findById(userId)
                .map(current -> current.choice(PROFILE_COLLECTION))
                .filter(choice -> choice.status() == AGREED)
                .filter(choice -> PROFILE_DOCUMENT_VERSION.equals(choice.documentVersion()))
                .isPresent();
        if (!agreed) {
            throw new BusinessException(ErrorCode.PROFILE_CONSENT_REQUIRED);
        }
    }

    private void lockMember(Long userId) {
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    private boolean matchesFirstChoice(Long userId, ConsentType type, ConsentChoice.Action action, String version) {
        return history.findFirstByUserIdAndConsentTypeOrderByIdAsc(userId, type)
                .filter(entry -> entry.matches(action, version)).isPresent();
    }

    private void withdraw(PrivacyConsents current, Long userId, ConsentType type) {
        var previous = current.choice(type);
        if (previous.status() == AGREED) {
            record(current, userId, type, WITHDRAW, previous.documentVersion(), clock.instant());
        }
    }

    private void record(PrivacyConsents current, Long userId, ConsentType type,
                        ConsentChoice.Action action, String version, Instant at) {
        current.change(type, new ConsentChoice(action.status(), version, at));
        history.save(new ConsentHistory(userId, type, action, version, at));
    }

    private void validateVersion(ConsentType type, String version) {
        if (version == null || version.length() > 20 || !version.matches("[0-9]+(?:\\.[0-9]+)*")) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_VERSION);
        }
        if (!currentVersion(type).equals(version)) {
            throw new BusinessException(ErrorCode.CONSENT_VERSION_CONFLICT);
        }
    }

    private String currentVersion(ConsentType type) {
        return switch (type) {
            case PROFILE_COLLECTION -> PROFILE_DOCUMENT_VERSION;
            case AI_CERT_TRAINING -> AI_DOCUMENT_VERSION;
        };
    }
}
