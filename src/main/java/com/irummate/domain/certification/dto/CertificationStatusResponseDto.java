package com.irummate.domain.certification.dto;

import com.irummate.domain.certification.entity.Certification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CertificationStatusResponseDto {

    private final String certificationId;
    private final String userId;
    private final String imageKey;
    private final String status;
    private final String semester;
    private final String adminComment;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public static CertificationStatusResponseDto from(Certification certification, String encodedUserId, String certificationId) {
        return CertificationStatusResponseDto.builder()
                .certificationId(certificationId)
                .userId(encodedUserId)
                .imageKey(certification.getImageKey())
                .status(certification.getCertificationStatus().name())
                .semester(certification.getSemester())
                .adminComment(certification.getAdminComment())
                .createdAt(certification.getCreatedAt())
                .expiresAt(certification.getExpiresAt())
                .build();
    }

    public static CertificationStatusResponseDto none(String encodedUserId) {
        return CertificationStatusResponseDto.builder()
                .userId(encodedUserId)
                .status("NONE")
                .build();
    }
}
