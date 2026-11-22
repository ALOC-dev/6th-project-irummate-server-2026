package com.irummate.domain.notification.repository;

import com.irummate.domain.notification.dto.NotificationResponses;
import com.irummate.domain.notification.dto.PushPreferences;
import com.irummate.domain.notification.entity.NotificationCategory;
import com.irummate.domain.notification.service.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcClient jdbc;

    public List<NotificationResponses.Item> list(long userId, NotificationCategory category,
                                                 LocalDateTime cursorAt, Long cursorId, int limit) {
        String categoryFilter = category == null ? "" : " AND category = :category";
        String cursorFilter = cursorAt == null ? "" : " AND (created_at, id) < (:cursorAt, :cursorId)";
        var query = jdbc.sql("""
                SELECT id, category, type, title, body, target_type, target_id, created_at, read_at
                FROM notifications
                WHERE recipient_id = :userId
                """ + categoryFilter + cursorFilter + " ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("userId", userId).param("limit", limit);
        if (category != null) {
            query.param("category", category.name());
        }
        if (cursorAt != null) {
            query.param("cursorAt", cursorAt.atZone(SEOUL).toOffsetDateTime()).param("cursorId", cursorId);
        }
        return query.query((row, index) -> new NotificationResponses.Item(
                row.getLong("id"), NotificationCategory.valueOf(row.getString("category")), row.getString("type"),
                row.getString("title"), row.getString("body"), row.getString("target_type"), row.getString("target_id"),
                toSeoul(row.getObject("created_at", OffsetDateTime.class)),
                toSeoul(row.getObject("read_at", OffsetDateTime.class))
        )).list();
    }

    public long countUnread(long userId) {
        return jdbc.sql("SELECT count(*) FROM notifications WHERE recipient_id = :userId AND read_at IS NULL")
                .param("userId", userId).query(Long.class).single();
    }

    public boolean markRead(long userId, long notificationId) {
        return jdbc.sql("""
                UPDATE notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE id = :notificationId AND recipient_id = :userId
                """).param("userId", userId).param("notificationId", notificationId).update() > 0;
    }

    public PushPreferences preferences(long userId) {
        return jdbc.sql("SELECT * FROM notification_preferences WHERE user_id = :userId")
                .param("userId", userId)
                .query((row, index) -> new PushPreferences(
                        row.getBoolean("paused"), row.getBoolean("chat"), row.getBoolean("matching"),
                        row.getBoolean("mate"), row.getBoolean("dorm_notice"), row.getBoolean("certification"),
                        row.getBoolean("recruitment"), row.getBoolean("admin_notice")))
                .optional().orElseGet(PushPreferences::defaults);
    }

    public void updatePreferences(long userId, PushPreferences request) {
        // 요청에 담긴 설정만 바꾼다.
        jdbc.sql("""
                INSERT INTO notification_preferences
                    (user_id, paused, chat, matching, mate, dorm_notice, certification, recruitment, admin_notice)
                VALUES (:userId, COALESCE(:paused, false), COALESCE(:chat, true), COALESCE(:matching, true),
                    COALESCE(:mate, true), COALESCE(:dorm, true), COALESCE(:certification, true),
                    COALESCE(:recruitment, true), COALESCE(:admin, true))
                ON CONFLICT (user_id) DO UPDATE SET
                    paused = COALESCE(:paused, notification_preferences.paused),
                    chat = COALESCE(:chat, notification_preferences.chat),
                    matching = COALESCE(:matching, notification_preferences.matching),
                    mate = COALESCE(:mate, notification_preferences.mate),
                    dorm_notice = COALESCE(:dorm, notification_preferences.dorm_notice),
                    certification = COALESCE(:certification, notification_preferences.certification),
                    recruitment = COALESCE(:recruitment, notification_preferences.recruitment),
                    admin_notice = COALESCE(:admin, notification_preferences.admin_notice),
                    updated_at = CURRENT_TIMESTAMP
                """).param("userId", userId)
                .param("paused", request.paused(), Types.BOOLEAN)
                .param("chat", request.chat(), Types.BOOLEAN)
                .param("matching", request.matching(), Types.BOOLEAN)
                .param("mate", request.mate(), Types.BOOLEAN)
                .param("dorm", request.dormNotice(), Types.BOOLEAN)
                .param("certification", request.certification(), Types.BOOLEAN)
                .param("recruitment", request.recruitment(), Types.BOOLEAN)
                .param("admin", request.adminNotice(), Types.BOOLEAN).update();
    }

    public boolean registerAnnouncement(String eventKey) {
        return jdbc.sql("""
                INSERT INTO admin_notification_dispatches (event_key) VALUES (:eventKey)
                ON CONFLICT (event_key) DO NOTHING
                """).param("eventKey", eventKey).update() > 0;
    }

    public List<Long> insertInbox(NotificationEvent event, Collection<Long> recipients) {
        // 공개 공지는 활성 계정 전체의 알림함에 저장한다.
        String filter = recipients == null ? "" : " AND id IN (:recipients)";
        var query = jdbc.sql("""
                INSERT INTO notifications (recipient_id, category, type, event_key, title, body, target_type, target_id)
                SELECT id, :category, :type, :eventKey, :title, :body, :targetType, :targetId
                FROM users WHERE status = 'ACTIVE'
                """ + filter + " ON CONFLICT (recipient_id, event_key) DO NOTHING"
                + (recipients == null ? "" : " RETURNING id"))
                .param("category", event.category().name()).param("type", event.type()).param("eventKey", event.key())
                .param("title", event.title()).param("body", event.body()).param("targetType", event.targetType())
                .param("targetId", event.targetId(), Types.VARCHAR);
        if (recipients == null) {
            query.update();
            return List.of();
        }
        return query.param("recipients", recipients).query(Long.class).list();
    }

    private static LocalDateTime toSeoul(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SEOUL).toLocalDateTime();
    }
}
