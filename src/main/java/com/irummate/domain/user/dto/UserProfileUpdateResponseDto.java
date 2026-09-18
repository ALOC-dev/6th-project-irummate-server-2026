package com.irummate.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileUpdateResponseDto {

    private final String nickname;
    private final String profileImageUrl;
}
