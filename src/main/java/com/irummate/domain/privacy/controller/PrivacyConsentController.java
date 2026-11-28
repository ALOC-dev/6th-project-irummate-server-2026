package com.irummate.domain.privacy.controller;

import com.irummate.domain.privacy.dto.ConsentRequests.ChangeRequest;
import com.irummate.domain.privacy.dto.ConsentRequests.OnboardingRequest;
import com.irummate.domain.privacy.dto.ConsentsResponse;
import com.irummate.domain.privacy.entity.ConsentChoice;
import com.irummate.domain.privacy.entity.ConsentType;
import com.irummate.domain.privacy.service.PrivacyConsentService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/privacy/consents")
@RequiredArgsConstructor
public class PrivacyConsentController {
    private final PrivacyConsentService service;

    @GetMapping("/me")
    public GlobalApiResponse<ConsentsResponse> findMine(@AuthenticationPrincipal Long userId) {
        return GlobalApiResponse.success(HttpStatus.OK, "동의 상태 조회 성공",
                new ConsentsResponse(service.findMyConsents(userId)));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Void> submit(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody OnboardingRequest request) {
        var profile = request.profileCollection();
        var ai = request.aiCertTraining();
        service.submitOnboarding(userId, request.termsOfService().agreed(), request.termsOfService().documentVersion(), profile.agreed(), profile.documentVersion(), ai.agreed(), ai.documentVersion());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{type}")
    public ResponseEntity<Void> change(@AuthenticationPrincipal Long userId, @PathVariable String type,
                                       @Valid @RequestBody ChangeRequest request) {
        ConsentType consentType;
        try { consentType = ConsentType.valueOf(type); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_CONSENT_TYPE); }
        if (consentType == ConsentType.AI_CERT_TRAINING) {
            if (request.action() == ConsentChoice.Action.DECLINE)
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            service.changeAiConsent(userId, request.action(), request.documentVersion());
        } else {
            service.agreeRequiredConsent(userId, consentType, request.action(), request.documentVersion());
        }
        return ResponseEntity.noContent().build();
    }
}
