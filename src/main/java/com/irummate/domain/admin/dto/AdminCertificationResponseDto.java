package com.irummate.domain.admin.dto;

import com.irummate.domain.certification.entity.Certification;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AdminCertificationResponseDto {

    private UUID certificationId;
    private Long version;
    private String userId;
    private String userRealName;
    private String userNickname;
    private String semester;
    private String imageURL;
    private String status;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime expiresAt;

    public static AdminCertificationResponseDto from(Certification certification,
                                                     UUID certificationId,
                                                     String userId,
                                                     String imageURL) {


        return new AdminCertificationResponseDto(
                certificationId,
                certification.getVersion(),
                userId,
                certification.getUser().getUserDetails() == null ? null : certification.getUser().getUserDetails().getRealName(),
                certification.getUser().getNickname(),
                certification.getSemester(),
                imageURL,
                certification.getCertificationStatus().name(),
                certification.getAdminComment(),
                certification.getCreatedAt(),
                certification.getReviewedAt(),
                certification.getExpiresAt()
        );
    }
}
