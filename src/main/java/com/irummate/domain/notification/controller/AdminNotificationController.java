package com.irummate.domain.notification.controller;

import com.irummate.domain.notification.dto.AdminNotificationRequest;
import com.irummate.domain.notification.service.NotificationService;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import com.irummate.global.security.AccessTokenAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {
    private final NotificationService notifications;
    private final AccessTokenAuthenticationService accessTokens;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GlobalApiResponse<AdminNotificationRequest.Accepted> publish(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @Valid @RequestBody AdminNotificationRequest request
    ) {
        if (!authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        var session = accessTokens.authenticate(authorization.substring(7));
        if (session.role() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ONLY);
        }
        // 전체 공개 공지는 토픽으로 발송한다.
        notifications.publishAnnouncement(session.userId(), request);
        return GlobalApiResponse.success(HttpStatus.ACCEPTED, "관리자 공지 발행 접수",
                new AdminNotificationRequest.Accepted(request.requestId()));
    }
}
