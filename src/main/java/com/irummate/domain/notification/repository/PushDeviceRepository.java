package com.irummate.domain.notification.repository;

import com.irummate.domain.notification.dto.PushDeviceRequest;
import com.irummate.global.security.AccessTokenAuthenticationService.AuthenticatedAccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PushDeviceRepository {
    private final JdbcClient jdbc;

    public boolean register(AuthenticatedAccessToken session, UUID installationId, PushDeviceRequest request) {
        // 다른 계정의 유효한 기기 등록은 기존 토큰과 일치할 때만 교체한다.
        return jdbc.sql("""
                INSERT INTO push_devices (installation_id, login_session_id, platform, fcm_token)
                SELECT :installationId, s.id, :platform, :token
                  FROM login_sessions s JOIN users u ON u.id = s.user_id
                 WHERE s.id = :sessionId AND u.id = :userId AND u.status = 'ACTIVE'
                   AND s.revoked_at IS NULL AND s.expires_at > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                ON CONFLICT (installation_id) DO UPDATE SET
                    login_session_id = EXCLUDED.login_session_id, platform = EXCLUDED.platform,
                    fcm_token = EXCLUDED.fcm_token, updated_at = CURRENT_TIMESTAMP, disabled_at = NULL
                WHERE push_devices.disabled_at IS NOT NULL OR push_devices.fcm_token = EXCLUDED.fcm_token
                   OR EXISTS (
                       SELECT 1 FROM login_sessions previous
                       WHERE previous.id = push_devices.login_session_id
                         AND (previous.user_id = :userId OR previous.revoked_at IS NOT NULL
                             OR previous.expires_at <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul'))
                   )
                """).param("sessionId", session.sessionId()).param("userId", session.userId())
                .param("installationId", installationId).param("platform", request.platform().name())
                .param("token", request.token()).update() > 0;
    }

    public void disable(AuthenticatedAccessToken session, UUID installationId) {
        jdbc.sql("""
                UPDATE push_devices SET disabled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, fcm_token = NULL
                WHERE installation_id = :installationId AND login_session_id = :sessionId AND disabled_at IS NULL
                """).param("installationId", installationId).param("sessionId", session.sessionId()).update();
    }

    public void invalidate(String token) {
        jdbc.sql("""
                UPDATE push_devices SET disabled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, fcm_token = NULL
                WHERE fcm_token = :token
                """).param("token", token).update();
    }
}
