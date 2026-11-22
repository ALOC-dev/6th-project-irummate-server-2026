package com.irummate.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irummate.domain.certification.entity.Certification;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AdminCertificationResponseDto {

    private final UUID certificationId;
    private final Long version;
    private final String userId;
    private final String realName;
    private final String nickname;
    private final String semester;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String imageUrl;
    private final String status;
    private final String adminComment;
    private final LocalDateTime createdAt;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime expiresAt;

    public static AdminCertificationResponseDto from(Certification certification,
                                                     String userId,
                                                     String imageUrl) {
        var user = certification.getUser();
        var details = user.getUserDetails();

        return new AdminCertificationResponseDto(
                certification.getPublicId(),
                certification.getVersion(),
                userId,
                details == null ? null : details.getRealName(),
                user.getNickname(),
                certification.getSemester(),
                imageUrl,
                certification.getCertificationStatus().name(),
                certification.getAdminComment(),
                certification.getCreatedAt(),
                certification.getReviewedAt(),
                certification.getExpiresAt()
        );
    }
}
