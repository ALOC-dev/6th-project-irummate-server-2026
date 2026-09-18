package com.irummate.domain.admin.dto;

import com.irummate.domain.user.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserResponseDto {

    private String userId;
    private String email;
    private String nickname;
    private String realName;
    private String gender;
    private String profileImageUrl;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    public static AdminUserResponseDto from(Users user, String userId) {
        return new AdminUserResponseDto(
                userId,
                user.getEmail(),
                user.getNickname(),
                user.getUserDetails() == null ? null : user.getUserDetails().getRealName(),
                user.getUserDetails() == null ? null : user.getUserDetails().getGender(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
