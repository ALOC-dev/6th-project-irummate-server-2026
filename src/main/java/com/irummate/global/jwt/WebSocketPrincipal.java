package com.irummate.global.jwt;

import java.security.Principal;

public class WebSocketPrincipal implements Principal {

    private final Long userId;

    public WebSocketPrincipal(Long userId) {
        this.userId = userId;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public Long getUserId() {
        return userId;
    }
}
