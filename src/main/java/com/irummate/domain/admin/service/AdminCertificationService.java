package com.irummate.domain.admin.service;

import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationResponseDto;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCertificationService {

    private final CertificationRepository certificationRepository;
    private final HashIdsUtils hashIdsUtils;
    private final S3Utils s3Utils;

    public List<AdminCertificationResponseDto> getCertifications(CertificationStatus status, int page) {
        Page<Certification> certifications = (status == null)
                ? certificationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, 15))
                : certificationRepository.findAllByCertificationStatusOrderByCreatedAtDesc(status, PageRequest.of(page, 15));

        return certifications.getContent().stream()
                .map(this::toAdminCertificationResponseDto)
                .toList();

    }

    public AdminCertificationResponseDto getCertification(String certificationId) {
        Certification certification = getCertificationEntity(certificationId);
        return AdminCertificationResponseDto.from(certification,
                certificationId,hashIdsUtils.encode(certification.getUser().getId()),
                s3Utils.createDownloadUrl(certification.getImageKey()));

    }

    @Transactional
    public AdminCertificationResponseDto approveCertification(String certificationId) {
        Certification certification = getCertificationEntity(certificationId);
        ensurePending(certification);

        certification.approve(null);
        certification.getUser().activate();

        return AdminCertificationResponseDto.from(certification,
                certificationId,
                hashIdsUtils.encode(certification.getUser().getId()),
                null);
    }

    @Transactional
    public AdminCertificationResponseDto rejectCertification(String certificationId, AdminCertificationRejectRequestDto requestDto) {
        Certification certification = getCertificationEntity(certificationId);
        ensurePending(certification);

        certification.reject(requestDto.getAdminComment());

        return AdminCertificationResponseDto.from(certification,
                certificationId,hashIdsUtils.encode(certification.getUser().getId()),
                null);
    }

    private Certification getCertificationEntity(String certificationId) {
        Long decodedCertificationId;
        try {
            decodedCertificationId = hashIdsUtils.decode(certificationId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        return certificationRepository.findById(decodedCertificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));
    }

    private void ensurePending(Certification certification) {
        if (certification.getCertificationStatus() != CertificationStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.CERTIFICATION_ALREADY_PROCESSED);
        }
    }

    private AdminCertificationResponseDto toAdminCertificationResponseDto(Certification certification) {
        return AdminCertificationResponseDto.from(
                certification,
                hashIdsUtils.encode(certification.getId()),
                hashIdsUtils.encode(certification.getUser().getId()),
                null
        );
    }
}
