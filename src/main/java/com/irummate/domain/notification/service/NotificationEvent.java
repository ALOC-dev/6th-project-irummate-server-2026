package com.irummate.domain.notification.service;

import com.irummate.domain.notification.entity.NotificationCategory;

import java.util.Objects;

public record NotificationEvent(NotificationCategory category, String type, String sourceId, String title,
                                String body, String targetType, String targetId) {
    public NotificationEvent {
        Objects.requireNonNull(category);
        requireText(type, 50);
        requireText(sourceId, 120);
        requireText(title, 100);
        requireText(body, 500);
        requireText(targetType, 40);
        if (targetId != null) {
            requireText(targetId, 128);
        }
    }

    public String key() {
        // 같은 이벤트를 다시 처리해도 같은 키를 사용한다.
        return category.name() + ":" + type + ":" + sourceId;
    }

    private static void requireText(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) {
            throw new IllegalArgumentException("알림 이벤트 값이 비어 있거나 너무 깁니다.");
        }
    }
}
