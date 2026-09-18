package com.irummate.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthStatusResponseDto {

    private final boolean authenticated;
    private final UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private final String id;
        private final String nickname;
        private final String role;
        private final String status;
        private final String certificationStatus;
        private final Boolean surveyCompleted;
    }
}
