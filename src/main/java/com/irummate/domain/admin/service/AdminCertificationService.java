package com.irummate.domain.admin.service;

import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationApproveRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationResponseDto;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCertificationService {

    private final CertificationRepository certificationRepository;
    private final HashIdsUtils hashIdsUtils;
    private final S3Utils s3Utils;
    private final UsersRepository usersRepository;

    public List<AdminCertificationResponseDto> getCertifications(CertificationStatus status, int page) {
        // 페이지 범위는 조회 전에 검증
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Page<Certification> certifications = (status == null)
                ? certificationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, 15))
                : certificationRepository.findAllByCertificationStatusOrderByCreatedAtDesc(status, PageRequest.of(page, 15));

        return certifications.getContent().stream()
                .map(this::toAdminCertificationResponseDto)
                .toList();

    }

    public AdminCertificationResponseDto getCertification(UUID certificationId) {
        Certification certification = getCertificationEntity(certificationId);
        return AdminCertificationResponseDto.from(certification,
                certification.getPublicId(),hashIdsUtils.encode(certification.getUser().getId()),
                s3Utils.createDownloadUrl(certification.getImageKey()));

    }

    @Transactional
    public AdminCertificationResponseDto approveCertification(Long adminUserId, UUID certificationId, AdminCertificationApproveRequestDto requestDto) {
        Certification certification = getCertificationEntity(certificationId);
        ensureReviewVersion(certification, requestDto.getExpectedVersion());
        ensurePending(certification);
        ensureCurrentSemester(certification, SemesterUtils.currentSemester());
        Users admin = getAdmin(adminUserId);

        certification.approve(admin, null, semesterExpiresAt(certification.getSemester()));
        // 갱신된 버전을 응답에 반영하고 낙관적 잠금 충돌 확인
        certificationRepository.flush();

        return AdminCertificationResponseDto.from(certification,
                certification.getPublicId(),
                hashIdsUtils.encode(certification.getUser().getId()),
                null);
    }

    @Transactional
    public AdminCertificationResponseDto rejectCertification(Long adminUserId, UUID certificationId, AdminCertificationRejectRequestDto requestDto) {
        Certification certification = getCertificationEntity(certificationId);
        ensureReviewVersion(certification, requestDto.getExpectedVersion());
        ensurePending(certification);
        Users admin = getAdmin(adminUserId);

        certification.reject(admin, requestDto.getAdminComment());
        certificationRepository.flush();

        return AdminCertificationResponseDto.from(certification,
                certification.getPublicId(),hashIdsUtils.encode(certification.getUser().getId()),
                null);
    }

    // 공개 UUID로 인증 요청 조회
    private Certification getCertificationEntity(UUID certificationId) {
        return certificationRepository.findByPublicId(certificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));
    }

    // 재제출 등으로 심사 대상이 바뀌었으면 다시 조회 후 심사
    private void ensureReviewVersion(Certification certification, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(certification.getVersion())) {
            throw new BusinessException(ErrorCode.UPDATE_CONFLICT);
        }
    }

    private void ensurePending(Certification certification) {
        if (certification.getCertificationStatus() != CertificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CERTIFICATION_ALREADY_PROCESSED);
        }
    }

    // 지난 학기 요청은 스케줄러 처리 여부와 관계없이 승인 불가
    private void ensureCurrentSemester(Certification certification, String currentSemester) {
        if (!currentSemester.equals(certification.getSemester())) {
            throw new BusinessException(ErrorCode.CERTIFICATION_SEMESTER_MISMATCH);
        }
    }

    private Users getAdmin(Long adminUserId) {
        return usersRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // 승인하는 날짜 대신 인증 기록의 학기를 기준으로 만료일 계산
    private LocalDateTime semesterExpiresAt(String semester) {
        String[] parts = semester.split("-");
        int year = Integer.parseInt(parts[0]);
        LocalDate end = "1".equals(parts[1])
                ? LocalDate.of(year, 6, 30)
                : LocalDate.of(year, 12, 31);
        return end.atTime(23, 59, 59);
    }

    private AdminCertificationResponseDto toAdminCertificationResponseDto(Certification certification) {
        return AdminCertificationResponseDto.from(
                certification,
                certification.getPublicId(),
                hashIdsUtils.encode(certification.getUser().getId()),
                null
        );
    }
}
