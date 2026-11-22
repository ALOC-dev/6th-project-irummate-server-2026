package com.irummate.domain.notification.service;

import com.irummate.domain.notification.dto.AdminNotificationRequest;
import com.irummate.domain.notification.dto.NotificationResponses;
import com.irummate.domain.notification.dto.PushPreferences;
import com.irummate.domain.notification.entity.NotificationCategory;
import com.irummate.domain.notification.repository.NotificationRepository;
import com.irummate.domain.notification.repository.PushJobRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    public static final String ANNOUNCEMENT_TOPIC = "service-announcements";

    private final NotificationRepository inbox;
    private final PushJobRepository pushJobs;
    private final HashIdsUtils hashIds;

    public NotificationResponses.Page list(long userId, NotificationCategory category,
                                           LocalDateTime cursorAt, Long cursorId, int size) {
        if (size < 1 || size > 50 || (cursorAt == null) != (cursorId == null)
                || (cursorId != null && cursorId < 1) || category == NotificationCategory.CHAT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var page = inbox.list(userId, category, cursorAt, cursorId, size + 1);
        if (page.size() <= size) {
            return new NotificationResponses.Page(page, false, null);
        }
        var last = page.get(size - 1);
        return new NotificationResponses.Page(List.copyOf(page.subList(0, size)), true,
                new NotificationResponses.Cursor(last.createdAt(), last.id()));
    }

    public long countUnread(long userId) {
        return inbox.countUnread(userId);
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        if (!inbox.markRead(userId, notificationId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    public PushPreferences preferences(long userId) {
        return inbox.preferences(userId);
    }

    @Transactional
    public PushPreferences updatePreferences(long userId, PushPreferences request) {
        inbox.updatePreferences(userId, request);
        return inbox.preferences(userId);
    }

    @Transactional
    public void publish(NotificationEvent event, Collection<Long> recipients) {
        // 호출한 서비스의 트랜잭션에 함께 저장할 수 있다.
        publishToRecipients(event, Set.copyOf(recipients));
    }

    @Transactional
    public void publishAnnouncement(long adminId, AdminNotificationRequest request) {
        boolean all = request.audience() == AdminNotificationRequest.Audience.ALL;
        boolean hasRecipients = request.recipientIds() != null && !request.recipientIds().isEmpty();
        if (all == hasRecipients) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var event = new NotificationEvent(NotificationCategory.ADMIN_NOTICE, "ADMIN_ANNOUNCEMENT",
                adminId + ":" + request.requestId(), request.title(), request.body(), "NOTIFICATIONS", null);
        if (!inbox.registerAnnouncement(event.key())) {
            return;
        }
        if (all) {
            inbox.insertInbox(event, null);
            pushJobs.enqueueTopic(event, ANNOUNCEMENT_TOPIC);
            return;
        }
        var recipients = request.recipientIds().stream().map(hashIds::decode).collect(Collectors.toSet());
        publishToRecipients(event, recipients);
    }

    private void publishToRecipients(NotificationEvent event, Set<Long> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        if (event.category() == NotificationCategory.CHAT) {
            pushJobs.enqueueDevices(event, recipients, List.of());
            return;
        }
        var inboxIds = inbox.insertInbox(event, recipients);
        if (!inboxIds.isEmpty()) {
            pushJobs.enqueueDevices(event, recipients, inboxIds);
        }
    }
}
