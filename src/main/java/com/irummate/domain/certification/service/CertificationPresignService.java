package com.irummate.domain.certification.service;

import com.irummate.domain.certification.dto.CertificationPresignRequestDto;
import com.irummate.domain.certification.dto.CertificationPresignResponseDto;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.PresignedUrlResponse;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificationPresignService {


    private final S3Utils s3Utils;
    private final UsersRepository usersRepository;
    private final UserDetailsRepository userDetailsRepository;
    private final CertificationRepository certificationRepository;


    public CertificationPresignResponseDto createUploadUrl(Long userId, CertificationPresignRequestDto requestDto) {

        Users me = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(me.getRole() != UserRole.USER || me.getStatus() != UserStatus.PENDING){
            throw new BusinessException(ErrorCode.FORBIDDEN, "USER/PENDING 상태에서만 업로드 URL을 발급할 수 있습니다.");
        }

        if(!userDetailsRepository.existsById(userId)){
            throw new BusinessException(ErrorCode.USER_DETAILS_REQUIRED);
        }

        String semester = SemesterUtils.currentSemester();
        ensureUploadUrlIssuable(userId, semester);

        String dirName = "certifications/" + semester + "/" + userId;

        PresignedUrlResponse presignedUrlResponse = s3Utils.createUploadUrl(requestDto.getFileName(), requestDto.getContentType(), dirName);

        return CertificationPresignResponseDto.builder()
                .uploadUrl(presignedUrlResponse.presignedUrl())
                .imageKey(presignedUrlResponse.key())
                .semester(semester)
                .expiresAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(5))
                .build();
    }

    private void ensureUploadUrlIssuable(Long userId, String semester) {
        certificationRepository.findByUser_IdAndSemester(userId, semester)
                .filter(certification -> certification.getCertificationStatus() != CertificationStatus.REJECTED)
                .ifPresent(certification -> {
                    throw new BusinessException(ErrorCode.CERTIFICATION_ALREADY_EXISTS);
                });
    }
}
