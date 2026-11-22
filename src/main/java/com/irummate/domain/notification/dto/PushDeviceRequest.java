package com.irummate.domain.notification.dto;

import com.irummate.domain.notification.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushDeviceRequest(@NotBlank @Size(max = 2048) String token, @NotNull PushPlatform platform) {}
