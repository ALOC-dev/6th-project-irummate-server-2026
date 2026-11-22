package com.irummate.domain.notification.dto;

import com.irummate.domain.notification.entity.NotificationCategory;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationResponses {
    private NotificationResponses() {}

    public record Item(long id, NotificationCategory category, String type, String title, String body,
                       String targetType, String targetId, LocalDateTime createdAt, LocalDateTime readAt) {}

    public record Cursor(LocalDateTime createdAt, long id) {}

    public record Page(List<Item> items, boolean hasNext, Cursor nextCursor) {}

    public record UnreadCount(long totalUnreadCount) {}
}
