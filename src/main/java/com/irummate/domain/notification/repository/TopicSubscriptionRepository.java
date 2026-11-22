package com.irummate.domain.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TopicSubscriptionRepository {
    private static final String JOIN_DEVICES = """
            FROM push_topic_subscriptions t
            LEFT JOIN push_devices d ON d.fcm_token = t.fcm_token
            LEFT JOIN login_sessions s ON s.id = d.login_session_id
            LEFT JOIN users u ON u.id = s.user_id
            LEFT JOIN notification_preferences p ON p.user_id = u.id
            """;
    private static final String DESIRED = """
            (d.installation_id IS NOT NULL AND u.status = 'ACTIVE'
                AND s.revoked_at IS NULL AND s.expires_at > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                AND NOT COALESCE(p.paused, false) AND COALESCE(p.admin_notice, true))
            """;

    private final JdbcClient jdbc;

    public void track(String token) {
        jdbc.sql("""
                INSERT INTO push_topic_subscriptions (fcm_token) VALUES (:token)
                ON CONFLICT (fcm_token) DO NOTHING
                """).param("token", token).update();
    }

    public List<Change> claim(UUID lease, int limit) {
        // 기기와 세션, 푸시 설정에서 구독 여부를 계산한다.
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT t.fcm_token, %s AS desired
                    %s
                    WHERE t.subscribed IS DISTINCT FROM %s AND t.next_attempt_at <= CURRENT_TIMESTAMP
                      AND (t.lease_expires_at IS NULL OR t.lease_expires_at <= CURRENT_TIMESTAMP)
                    ORDER BY desired, t.next_attempt_at, t.fcm_token
                    LIMIT :limit FOR UPDATE OF t SKIP LOCKED
                )
                UPDATE push_topic_subscriptions t SET subscribed = NULL, lease_token = :lease,
                    lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '2 minutes'
                FROM candidates c WHERE t.fcm_token = c.fcm_token
                RETURNING t.fcm_token, c.desired
                """.formatted(DESIRED, JOIN_DEVICES, DESIRED)).param("lease", lease).param("limit", limit)
                .query((row, index) -> new Change(row.getString("fcm_token"), row.getBoolean("desired"))).list();
    }

    public boolean hasOutstandingChanges() {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 %s
                    WHERE t.subscribed IS DISTINCT FROM %s
                )
                """.formatted(JOIN_DEVICES, DESIRED)).query(Boolean.class).single();
    }

    public void finish(String token, UUID lease, Boolean subscribed) {
        jdbc.sql("""
                UPDATE push_topic_subscriptions SET subscribed = :subscribed, lease_token = NULL, lease_expires_at = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP + :delay * INTERVAL '1 second'
                WHERE fcm_token = :token AND lease_token = :lease
                """).param("token", token).param("lease", lease).param("subscribed", subscribed, Types.BOOLEAN)
                .param("delay", subscribed == null ? 30 : 0).update();
    }

    public record Change(String token, boolean subscribe) {}
}
