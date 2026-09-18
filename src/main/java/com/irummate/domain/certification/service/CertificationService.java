package com.irummate.domain.certification.service;

import com.irummate.domain.certification.dto.CertificationRequestDto;
import com.irummate.domain.certification.dto.CertificationResponseDto;
import com.irummate.domain.certification.dto.CertificationStatusResponseDto;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final UsersRepository usersRepository;
    private final UserDetailsRepository userDetailsRepository;
    private final HashIdsUtils hashIdsUtils;
    private final S3Utils s3Utils;

    @Transactional
    public CertificationResponseDto createCertification(Long userId, CertificationRequestDto requestDto) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateCertificationEligibility(userId, user);
        String semester = SemesterUtils.currentSemester();

        Optional<Certification> existingCertification = certificationRepository.findByUser_IdAndSemester(userId, semester);
        validateCertificationSubmittable(existingCertification);

        validateImageKey(userId, semester, requestDto.getImageKey());
        validateUploadedImage(requestDto.getImageKey());

        if (existingCertification.isPresent()) {
            Certification certification = existingCertification.get();
            String previousImageKey = certification.getImageKey();
            certification.resubmit(requestDto.getImageKey());
            deletePreviousImage(previousImageKey, requestDto.getImageKey());

            return CertificationResponseDto.from(certification,
                    hashIdsUtils.encode(user.getId()),
                    hashIdsUtils.encode(certification.getId()));
        }

        Certification certification = Certification.builder()
                .user(user)
                .semester(semester)
                .imageKey(requestDto.getImageKey())
                .certificationStatus(CertificationStatus.REQUESTED)
                .build();

        Certification savedCertification = certificationRepository.save(certification);
        return CertificationResponseDto.from(savedCertification,
                hashIdsUtils.encode(user.getId()),
                hashIdsUtils.encode(savedCertification.getId()));
    }

    public CertificationStatusResponseDto getLatestCertificationStatus(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Certification certification = certificationRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));

        return CertificationStatusResponseDto.from(certification,
                hashIdsUtils.encode(user.getId()),
                hashIdsUtils.encode(certification.getId()));
    }

    private void validateCertificationEligibility(Long userId, Users user) {
        if (user.getRole() != UserRole.USER || user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "USER/PENDING 상태에서만 인증 요청이 가능합니다.");
        }

        if (!userDetailsRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_DETAILS_REQUIRED);
        }
    }

    private void validateCertificationSubmittable(Optional<Certification> certification) {
        certification
                .filter(existingCertification -> existingCertification.getCertificationStatus() != CertificationStatus.REJECTED)
                .ifPresent(existingCertification -> {
                    throw new BusinessException(ErrorCode.CERTIFICATION_ALREADY_EXISTS);
                });
    }

    private void validateImageKey(Long userId, String semester, String imageKey) {
        String expectedPrefix = "certifications/" + semester + "/" + userId + "/";

        if (!imageKey.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateUploadedImage(String imageKey) {
        try {
            s3Utils.validateUploadedImage(imageKey);
        } catch (BusinessException e) {
            try {
                s3Utils.delete(imageKey);
            } catch (Exception deleteException) {
                log.warn("Failed to delete invalid certification image. key={}", imageKey, deleteException);
            }
            throw e;
        }
    }

    private void deletePreviousImage(String previousImageKey, String currentImageKey) {
        if (previousImageKey.equals(currentImageKey)) {
            return;
        }

        try {
            s3Utils.delete(previousImageKey);
        } catch (Exception e) {
            log.warn("Failed to delete previous certification image. key={}", previousImageKey, e);
        }
    }
}
