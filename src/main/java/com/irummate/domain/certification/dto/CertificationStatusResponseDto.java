package com.irummate.domain.certification.dto;

import com.irummate.domain.certification.entity.Certification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CertificationStatusResponseDto {

    private final UUID certificationId;
    private final String userId;
    private final String imageKey;
    private final String status;
    private final String semester;
    private final String adminComment;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public static CertificationStatusResponseDto from(Certification certification, String encodedUserId, UUID certificationId) {
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
