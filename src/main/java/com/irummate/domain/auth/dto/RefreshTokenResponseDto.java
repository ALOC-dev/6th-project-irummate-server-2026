package com.irummate.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshTokenResponseDto {

    private final String accessToken;
}
