package com.irummate.global.jwt;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

public class WebSocketPrincipal implements Principal {

    private final Long userId;
    private final UUID sessionId;
    private final Instant expiresAt;

    public WebSocketPrincipal(Long userId) {
        this(userId, null, null);
    }

    public WebSocketPrincipal(Long userId, UUID sessionId, Instant expiresAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getSessionId() { return sessionId; }

    public Instant getExpiresAt() { return expiresAt; }
}
