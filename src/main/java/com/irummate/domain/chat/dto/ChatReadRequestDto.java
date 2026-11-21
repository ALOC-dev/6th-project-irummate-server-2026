package com.irummate.domain.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatReadRequestDto(@NotNull @Positive Long lastReadMessageId) {
}
