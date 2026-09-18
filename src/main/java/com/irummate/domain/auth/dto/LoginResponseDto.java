package com.irummate.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponseDto {

    private final String accessToken;
    private final Boolean isNewUser;
    private final UserInfo user;

    private final String userId;
    private final String tokenType;
    private final Long accessTokenExpiresIn;

    @Getter
    @Builder
    public static class UserInfo {
        private final String id;
        private final String nickname;
        private final String role;
        private final String status;
    }
}
