package com.irummate.domain.certification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CertificationPresignResponseDto {

    private final String uploadUrl;
    private final String imageKey;
    private final String semester;
    private final LocalDateTime expiresAt;
}
