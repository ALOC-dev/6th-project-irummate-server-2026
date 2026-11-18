package com.irummate.domain.certification.service;

import com.irummate.domain.admin.service.AdminCertificationService;
import com.irummate.domain.admin.dto.AdminCertificationApproveRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.certification.dto.CertificationRequestDto;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CertificationPublicIdTest {
    private final CertificationRepository repository = mock(CertificationRepository.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final UserDetailsRepository userDetailsRepository = mock(UserDetailsRepository.class);
    private final S3Utils s3Utils = mock(S3Utils.class);
    private final HashIdsUtils hashIdsUtils = new HashIdsUtils("certification-test", "8");
    private final CertificationService service = new CertificationService(
            repository, usersRepository, userDetailsRepository, hashIdsUtils, s3Utils,
            Clock.system(ZoneId.of("Asia/Seoul")));
    private final AdminCertificationService adminService = new AdminCertificationService(
            repository, hashIdsUtils, s3Utils, usersRepository);
    private final Users user = Users.builder().oauthId("certification-test").nickname("사용자").build();
    private final String semester = SemesterUtils.currentSemester();
    private final String imageKey = "certifications/" + semester + "/7/new.png";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", 7L);
        when(usersRepository.findById(7L)).thenReturn(Optional.of(user));
        when(usersRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(userDetailsRepository.existsById(7L)).thenReturn(true);
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'"))
                .thenReturn(mock(Query.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void creationReturnsSavedPublicUuid() {
        Certification saved = certification(CertificationStatus.PENDING);
        when(repository.saveAndFlush(any(Certification.class))).thenReturn(saved);

        var response = service.createCertification(7L, request());

        assertThat(response.getCertificationId()).isEqualTo(saved.getPublicId());
        assertThat(response.getUserId()).isEqualTo(hashIdsUtils.encode(7L));
    }

    @Test
    void resubmissionKeepsExistingPublicUuid() {
        Certification existing = certification(CertificationStatus.REJECTED);
        UUID originalId = existing.getPublicId();
        when(repository.findByUser_IdAndSemester(7L, semester)).thenReturn(Optional.of(existing));

        var response = service.createCertification(7L, request());

        assertThat(response.getCertificationId()).isEqualTo(originalId);
        assertThat(existing.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void currentSemesterStatusReturnsPublicUuid() {
        Certification certification = certification(CertificationStatus.PENDING);
        when(repository.findByUser_IdAndSemester(7L, semester)).thenReturn(Optional.of(certification));

        assertThat(service.getLatestCertificationStatus(7L).getCertificationId())
                .isEqualTo(certification.getPublicId());
    }

    @Test
    void adminDetailLooksUpPublicUuidInsteadOfPrimaryKey() {
        Certification certification = certification(CertificationStatus.PENDING);
        when(repository.findByPublicId(certification.getPublicId())).thenReturn(Optional.of(certification));
        when(s3Utils.createDownloadUrl(certification.getImageKey())).thenReturn("https://example.com/image");

        var response = adminService.getCertification(certification.getPublicId());

        assertThat(response.getCertificationId()).isEqualTo(certification.getPublicId());
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void missingPublicUuidThrowsCertificationNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.getCertification(UUID.randomUUID()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CERTIFICATION_NOT_FOUND);
    }

    @Test
    void adminListReturnsPublicUuid() {
        Certification certification = certification(CertificationStatus.PENDING);
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(certification)));

        assertThat(adminService.getCertifications(null, 0).getFirst().getCertificationId())
                .isEqualTo(certification.getPublicId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-2", "2026-1", "2027-1"})
    void approvalRejectsRequestsOutsideCurrentSemester(String requestedSemester) {
        Certification certification = certification(CertificationStatus.PENDING);
        ReflectionTestUtils.setField(certification, "semester", requestedSemester);
        when(repository.findByPublicId(certification.getPublicId())).thenReturn(Optional.of(certification));

        try (var semesters = mockStatic(SemesterUtils.class)) {
            semesters.when(SemesterUtils::currentSemester).thenReturn("2026-2");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> adminService.approveCertification(2L, certification.getPublicId(), approvalRequest(0L)));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CERTIFICATION_SEMESTER_MISMATCH);
            assertThat(certification.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING);
            assertThat(certification.getReviewedBy()).isNull();
            assertThat(certification.getExpiresAt()).isNull();
            verify(usersRepository, never()).findById(2L);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026-1,2026-06-30T23:59:59",
            "2026-2,2026-12-31T23:59:59",
            "2027-1,2027-06-30T23:59:59"
    })
    void approvalUsesCurrentRequestSemesterForExpiration(String currentSemester, LocalDateTime expectedExpiration) {
        Certification certification = certification(CertificationStatus.PENDING);
        ReflectionTestUtils.setField(certification, "semester", currentSemester);
        when(repository.findByPublicId(certification.getPublicId())).thenReturn(Optional.of(certification));
        Users admin = Users.builder().oauthId("admin").nickname("관리자").build();
        when(usersRepository.findById(2L)).thenReturn(Optional.of(admin));

        try (var semesters = mockStatic(SemesterUtils.class)) {
            semesters.when(SemesterUtils::currentSemester).thenReturn(currentSemester);

            var response = adminService.approveCertification(2L, certification.getPublicId(), approvalRequest(0L));

            assertThat(response.getCertificationId()).isEqualTo(certification.getPublicId());
            assertThat(certification.getCertificationStatus()).isEqualTo(CertificationStatus.APPROVED);
            assertThat(certification.getReviewedBy()).isSameAs(admin);
            assertThat(certification.getExpiresAt()).isEqualTo(expectedExpiration);
            semesters.verify(SemesterUtils::currentSemester, times(1));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void negativeAdminPageIsRejectedBeforeQuery(int page) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.getCertifications(null, page));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(repository);
    }

    @Test
    void staleReviewVersionCannotApproveResubmittedCertification() {
        Certification certification = certification(CertificationStatus.PENDING);
        ReflectionTestUtils.setField(certification, "version", 2L);
        when(repository.findByPublicId(certification.getPublicId())).thenReturn(Optional.of(certification));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.approveCertification(2L, certification.getPublicId(), approvalRequest(0L)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UPDATE_CONFLICT);
        assertThat(certification.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING);
        verify(repository, never()).flush();
        verify(usersRepository, never()).findById(2L);
    }

    @Test
    void staleReviewVersionCannotRejectResubmittedCertification() {
        Certification certification = certification(CertificationStatus.PENDING);
        ReflectionTestUtils.setField(certification, "version", 2L);
        when(repository.findByPublicId(certification.getPublicId())).thenReturn(Optional.of(certification));
        AdminCertificationRejectRequestDto request = new AdminCertificationRejectRequestDto();
        ReflectionTestUtils.setField(request, "expectedVersion", 0L);
        ReflectionTestUtils.setField(request, "adminComment", "다시 제출해주세요.");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.rejectCertification(2L, certification.getPublicId(), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UPDATE_CONFLICT);
        assertThat(certification.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING);
        verify(repository, never()).flush();
    }

    private AdminCertificationApproveRequestDto approvalRequest(Long version) {
        AdminCertificationApproveRequestDto request = new AdminCertificationApproveRequestDto();
        ReflectionTestUtils.setField(request, "expectedVersion", version);
        return request;
    }

    private CertificationRequestDto request() {
        CertificationRequestDto request = new CertificationRequestDto();
        ReflectionTestUtils.setField(request, "imageKey", imageKey);
        return request;
    }

    private Certification certification(CertificationStatus status) {
        Certification certification = Certification.builder()
                .user(user).semester(semester).imageKey(imageKey).certificationStatus(status).build();
        ReflectionTestUtils.setField(certification, "id", 99L);
        ReflectionTestUtils.setField(certification, "version", 0L);
        ReflectionTestUtils.setField(certification, "publicId", UUID.randomUUID());
        return certification;
    }
}
