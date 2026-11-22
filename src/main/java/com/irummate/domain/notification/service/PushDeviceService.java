package com.irummate.domain.notification.service;

import com.irummate.domain.notification.dto.PushDeviceRequest;
import com.irummate.domain.notification.repository.PushDeviceRepository;
import com.irummate.domain.notification.repository.TopicSubscriptionRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.security.AccessTokenAuthenticationService;
import com.irummate.global.security.AccessTokenAuthenticationService.AuthenticatedAccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushDeviceService {
    private final AccessTokenAuthenticationService accessTokens;
    private final PushDeviceRepository devices;
    private final TopicSubscriptionRepository topicSubscriptions;

    @Transactional
    public void register(String authorization, UUID installationId, PushDeviceRequest request) {
        var session = authenticate(authorization);
        if (request.token().getBytes(StandardCharsets.UTF_8).length > 2048) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            if (!devices.register(session, installationId, request)) {
                throw new BusinessException(ErrorCode.UPDATE_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.UPDATE_CONFLICT);
        }
        // 스케줄러가 이 토큰의 구독 상태를 동기화한다.
        topicSubscriptions.track(request.token());
    }

    @Transactional
    public void disable(String authorization, UUID installationId) {
        devices.disable(authenticate(authorization), installationId);
    }

    private AuthenticatedAccessToken authenticate(String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return accessTokens.authenticate(authorization.substring(7));
    }
}
