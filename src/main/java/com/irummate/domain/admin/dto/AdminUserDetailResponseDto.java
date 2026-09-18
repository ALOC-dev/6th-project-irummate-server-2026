package com.irummate.domain.admin.dto;

import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.user.entity.UserDetails;
import com.irummate.domain.user.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserDetailResponseDto {

    private String userId;
    private String email;
    private String nickname;
    private String realName;
    private String phoneNumber;
    private String studentId;
    private String department;
    private Integer age;
    private String gender;
    private String profileImageUrl;
    private String role;
    private String status;
    private String certificationStatus;
    private Boolean surveyCompleted;
    private LocalDateTime createdAt;

    public static AdminUserDetailResponseDto from(Users user,
                                                  String userId,
                                                  Certification latestCertification) {
        UserDetails details = user.getUserDetails();
        UserPreferences preferences = user.getUserPreferences();

        return new AdminUserDetailResponseDto(
                userId,
                user.getEmail(),
                user.getNickname(),
                details == null ? null : details.getRealName(),
                details == null ? null : details.getPhoneNumber(),
                details == null ? null : details.getStudentId(),
                details == null ? null : details.getDepartment(),
                details == null ? null : details.getAge(),
                details == null ? null : details.getGender(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                latestCertification == null ? null : latestCertification.getCertificationStatus().name(),
                preferences != null && Boolean.TRUE.equals(preferences.getIsCompleted()),
                user.getCreatedAt()
        );
    }
}
