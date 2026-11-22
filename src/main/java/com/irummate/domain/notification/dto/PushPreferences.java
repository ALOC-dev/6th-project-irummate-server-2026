package com.irummate.domain.notification.dto;

public record PushPreferences(Boolean paused, Boolean chat, Boolean matching, Boolean mate, Boolean dormNotice,
                              Boolean certification, Boolean recruitment, Boolean adminNotice) {
    public static PushPreferences defaults() {
        return new PushPreferences(false, true, true, true, true, true, true, true);
    }
}
