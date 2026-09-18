package com.irummate.domain.certification.dto;

import com.irummate.domain.certification.entity.Certification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CertificationResponseDto {

    private final String certificationId;
    private final String userId;
    private final String semester;
    private final String imageKey;
    private final String certificationStatus;
    private final String adminComment;
    private final LocalDateTime createdAt;

    public static CertificationResponseDto from(Certification certification, String encodedUserId, String certificationId) {
        return CertificationResponseDto.builder()
                .certificationId(certificationId)
                .userId(encodedUserId)
                .semester(certification.getSemester())
                .imageKey(certification.getImageKey())
                .certificationStatus(certification.getCertificationStatus().name())
                .adminComment(certification.getAdminComment())
                .createdAt(certification.getCreatedAt())
                .build();
    }
}
