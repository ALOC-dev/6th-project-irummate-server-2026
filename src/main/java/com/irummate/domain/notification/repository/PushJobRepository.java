package com.irummate.domain.notification.repository;

import com.irummate.domain.notification.entity.NotificationCategory;
import com.irummate.domain.notification.entity.PushJobStatus;
import com.irummate.domain.notification.service.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PushJobRepository {
    private static final String ALLOWS_PUSH = """
            NOT COALESCE(p.paused, false) AND COALESCE(CASE %s
                WHEN 'MATCHING' THEN p.matching WHEN 'MATE' THEN p.mate WHEN 'CHAT' THEN p.chat
                WHEN 'DORM_NOTICE' THEN p.dorm_notice WHEN 'CERTIFICATION' THEN p.certification
                WHEN 'RECRUITMENT' THEN p.recruitment WHEN 'ADMIN_NOTICE' THEN p.admin_notice
            END, true)
            """;
    private static final String PAYLOAD = """
            jsonb_build_object('title', CAST(:title AS text), 'body', CAST(:body AS text),
                'data', jsonb_strip_nulls(jsonb_build_object('type', CAST(:type AS text),
                    'targetType', CAST(:targetType AS text), 'targetId', CAST(:targetId AS text))))
            """;

    private final JdbcClient jdbc;

    public void enqueueDevices(NotificationEvent event, Collection<Long> recipients, List<Long> inboxIds) {
        boolean chat = event.category() == NotificationCategory.CHAT;
        String inboxId = chat ? "NULL" : "n.id";
        String inboxJoin = chat ? "" : "JOIN notifications n ON n.recipient_id = u.id AND n.id IN (:inboxIds)";
        // 이 분야의 푸시를 켠 수신자의 기기만 일괄 등록한다.
        var query = payloadQuery(jdbc.sql("""
                INSERT INTO push_jobs (installation_id, recipient_id, login_session_id, notification_id,
                    category, event_key, payload, expires_at)
                SELECT d.installation_id, u.id, s.id, %s, :category, :eventKey, %s,
                    CURRENT_TIMESTAMP + CASE WHEN :category = 'CHAT' THEN INTERVAL '15 minutes'
                        ELSE INTERVAL '1 day' END
                FROM push_devices d
                JOIN login_sessions s ON s.id = d.login_session_id
                JOIN users u ON u.id = s.user_id
                LEFT JOIN notification_preferences p ON p.user_id = u.id
                %s
                WHERE u.id IN (:recipients) AND u.status = 'ACTIVE'
                  AND d.disabled_at IS NULL AND s.revoked_at IS NULL
                  AND s.expires_at > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                  AND %s
                ON CONFLICT (installation_id, recipient_id, event_key) DO NOTHING
                """.formatted(inboxId, PAYLOAD, inboxJoin, ALLOWS_PUSH.formatted(":category"))), event)
                .param("recipients", recipients);
        if (!chat) {
            query.param("inboxIds", inboxIds);
        }
        query.update();
    }

    public void enqueueTopic(NotificationEvent event, String topic) {
        payloadQuery(jdbc.sql("""
                INSERT INTO push_jobs (topic, category, event_key, payload, expires_at)
                VALUES (:topic, :category, :eventKey, %s, CURRENT_TIMESTAMP + INTERVAL '1 day')
                ON CONFLICT (topic, event_key) DO NOTHING
                """.formatted(PAYLOAD)), event).param("topic", topic).update();
    }

    public List<ClaimedJob> claim(UUID lease, int limit) {
        // 선점은 먼저 커밋하고, 만료된 선점은 다음 실행에서 회수한다.
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id FROM push_jobs
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND lease_expires_at <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, id LIMIT :limit FOR UPDATE SKIP LOCKED
                )
                UPDATE push_jobs j
                SET status = 'PROCESSING', lease_token = :lease, lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                    attempt_count = attempt_count + 1
                FROM candidates c WHERE j.id = c.id
                RETURNING j.id, j.topic, j.payload::text, j.attempt_count, j.expires_at <= CURRENT_TIMESTAMP AS expired
                """).param("lease", lease).param("limit", limit)
                .query((row, index) -> new ClaimedJob(row.getLong("id"), row.getString("topic"),
                        row.getString("payload"), row.getInt("attempt_count"), row.getBoolean("expired"))).list();
    }

    public Map<Long, String> eligibleDevices(Collection<Long> ids, UUID lease) {
        // 발송 직전에 세션과 최근 푸시 설정을 다시 확인한다.
        return jdbc.sql("""
                SELECT j.id, d.fcm_token FROM push_jobs j
                JOIN push_devices d ON d.installation_id = j.installation_id AND d.login_session_id = j.login_session_id
                JOIN login_sessions s ON s.id = j.login_session_id AND s.user_id = j.recipient_id
                JOIN users u ON u.id = s.user_id
                LEFT JOIN notification_preferences p ON p.user_id = u.id
                WHERE j.id IN (:ids) AND j.lease_token = :lease AND j.lease_expires_at > CURRENT_TIMESTAMP
                  AND j.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'
                  AND d.disabled_at IS NULL AND s.revoked_at IS NULL
                  AND s.expires_at > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                  AND %s
                """.formatted(ALLOWS_PUSH.formatted("j.category"))).param("ids", ids).param("lease", lease)
                .query((row, index) -> Map.entry(row.getLong("id"), row.getString("fcm_token"))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void finish(long id, UUID lease, PushJobStatus status, String code, int delaySeconds) {
        jdbc.sql("""
                UPDATE push_jobs SET status = :status, last_error_code = :code,
                    next_attempt_at = CURRENT_TIMESTAMP + :delay * INTERVAL '1 second',
                    finished_at = CASE WHEN :status = 'PENDING' THEN NULL ELSE CURRENT_TIMESTAMP END,
                    lease_token = NULL, lease_expires_at = NULL
                WHERE id = :id AND lease_token = :lease
                """).param("id", id).param("lease", lease).param("status", status.name())
                .param("code", code, Types.VARCHAR).param("delay", delaySeconds).update();
    }

    public void deferTopic(long id, UUID lease) {
        jdbc.sql("""
                UPDATE push_jobs SET status = 'PENDING', attempt_count = attempt_count - 1,
                    next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '10 seconds',
                    lease_token = NULL, lease_expires_at = NULL
                WHERE id = :id AND lease_token = :lease
                """).param("id", id).param("lease", lease).update();
    }

    private JdbcClient.StatementSpec payloadQuery(JdbcClient.StatementSpec query, NotificationEvent event) {
        String body = event.category() == NotificationCategory.CHAT ? "새 메시지가 도착했어요." : event.body();
        return query.param("category", event.category().name()).param("eventKey", event.key())
                .param("title", event.title()).param("body", body).param("type", event.type())
                .param("targetType", event.targetType()).param("targetId", event.targetId(), Types.VARCHAR);
    }

    public record ClaimedJob(long id, String topic, String payload, int attempts, boolean expired) {}
}
