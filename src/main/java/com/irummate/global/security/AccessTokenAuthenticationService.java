package com.irummate.global.security;

import com.irummate.domain.auth.repository.LoginSessionRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessTokenAuthenticationService {
    private final JwtTokenProvider tokenProvider;
    private final HashIdsUtils hashIds;
    private final LoginSessionRepository sessions;

    public AuthenticatedAccessToken authenticate(String token) {
        Long userId;
        UUID sessionId;
        Instant expiresAt;
        try {
            var claims = tokenProvider.parseClaims(token); // verifies signature and JWT expiry
            if (!"access".equals(claims.get("type", String.class)) || claims.getExpiration() == null) {
                throw unauthorized();
            }
            userId = hashIds.decode(claims.getSubject());
            String sid = claims.get("sid", String.class);
            if (sid == null) throw unauthorized();
            sessionId = UUID.fromString(sid);
            // UUID.fromString also accepts abbreviated groups; require the canonical JWT contract.
            if (!sessionId.toString().equalsIgnoreCase(sid)) throw unauthorized();
            expiresAt = claims.getExpiration().toInstant();
        } catch (JwtException | IllegalArgumentException | BusinessException e) {
            throw unauthorized();
        }
        return validateSession(userId, sessionId, expiresAt);
    }

    // Only server-authenticated, immutable CONNECT metadata may be passed here, never native frame headers.
    public AuthenticatedAccessToken validateSession(Long userId, UUID sessionId, Instant expiresAt) {
        if (userId == null || userId <= 0 || sessionId == null || expiresAt == null
                || !expiresAt.isAfter(Instant.now())) {
            throw unauthorized();
        }
        var session = sessions.findAuthenticationById(sessionId).orElseThrow(AccessTokenAuthenticationService::unauthorized);
        if (!userId.equals(session.getUserId()) || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                || session.getStatus() != UserStatus.ACTIVE) {
            throw unauthorized();
        }
        return new AuthenticatedAccessToken(userId, session.getRole(), sessionId, expiresAt);
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    public record AuthenticatedAccessToken(Long userId, UserRole role, UUID sessionId, Instant expiresAt) {}
}
