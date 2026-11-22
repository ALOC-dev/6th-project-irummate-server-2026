package com.irummate.domain.notification.controller;

import com.irummate.domain.notification.dto.NotificationResponses;
import com.irummate.domain.notification.dto.PushPreferences;
import com.irummate.domain.notification.entity.NotificationCategory;
import com.irummate.domain.notification.service.NotificationService;
import com.irummate.global.response.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    public GlobalApiResponse<NotificationResponses.Page> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") int size
    ) {
        var page = notifications.list(userId, category, cursorCreatedAt, cursorId, size);
        return GlobalApiResponse.multiSuccess(HttpStatus.OK, "알림 목록 조회 성공", page, page.items().size());
    }

    @GetMapping("/unread-count")
    public GlobalApiResponse<NotificationResponses.UnreadCount> unreadCount(@AuthenticationPrincipal Long userId) {
        return GlobalApiResponse.success(HttpStatus.OK, "안 읽은 알림 개수 조회 성공",
                new NotificationResponses.UnreadCount(notifications.countUnread(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public GlobalApiResponse<Void> markRead(@AuthenticationPrincipal Long userId, @PathVariable long notificationId) {
        notifications.markRead(userId, notificationId);
        return GlobalApiResponse.success(HttpStatus.OK, "알림 읽음 처리 성공", null);
    }

    @GetMapping("/preferences")
    public GlobalApiResponse<PushPreferences> preferences(@AuthenticationPrincipal Long userId) {
        return GlobalApiResponse.success(HttpStatus.OK, "푸시 설정 조회 성공", notifications.preferences(userId));
    }

    @PatchMapping("/preferences")
    public GlobalApiResponse<PushPreferences> updatePreferences(@AuthenticationPrincipal Long userId,
                                                                 @RequestBody PushPreferences request) {
        return GlobalApiResponse.success(HttpStatus.OK, "푸시 설정 수정 성공",
                notifications.updatePreferences(userId, request));
    }
}
