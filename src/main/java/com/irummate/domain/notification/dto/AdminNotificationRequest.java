package com.irummate.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AdminNotificationRequest(
        @NotNull UUID requestId,
        @NotNull Audience audience,
        @Size(max = 1000) List<@NotBlank @Size(max = 128) String> recipientIds,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String body
) {
    public enum Audience { ALL, SELECTED }

    public record Accepted(UUID requestId) {}
}
