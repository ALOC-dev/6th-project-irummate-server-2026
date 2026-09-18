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
    private final String adminComment;
    private final LocalDateTime createdAt;

    public static CertificationStatusResponseDto from(Certification certification, String encodedUserId, String certificationId) {
        return CertificationStatusResponseDto.builder()
                .certificationId(certificationId)
                .userId(encodedUserId)
                .imageKey(certification.getImageKey())
                .status(certification.getCertificationStatus().name())
                .adminComment(certification.getAdminComment())
                .createdAt(certification.getCreatedAt())
                .build();
    }
}
