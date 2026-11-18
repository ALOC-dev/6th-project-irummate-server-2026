package com.irummate.domain.certification.service;

import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.s3.S3Utils;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificationTimeTest {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void expirationUsesConfiguredClockIncludingExactBoundary(int offsetSeconds) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), SERVICE_ZONE);
        CertificationRepository repository = mock(CertificationRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        CertificationService service = new CertificationService(
                repository, usersRepository, mock(UserDetailsRepository.class),
                new HashIdsUtils("certification-time-test", "8"), mock(S3Utils.class), clock);
        Users user = Users.builder().oauthId("time-test").nickname("사용자").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        Certification certification = Certification.builder()
                .user(user).semester(SemesterUtils.currentSemester()).imageKey("test.png")
                .certificationStatus(CertificationStatus.APPROVED).build();
        ReflectionTestUtils.setField(certification, "expiresAt",
                LocalDateTime.now(clock).plusSeconds(offsetSeconds));
        when(usersRepository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.findByUser_IdAndSemester(7L, SemesterUtils.currentSemester()))
                .thenReturn(Optional.of(certification));

        String status = service.getLatestCertificationStatus(7L).getStatus();

        assertThat(status).isEqualTo(offsetSeconds <= 0 ? "EXPIRED" : "APPROVED");
    }

    @Test
    void creationAndReviewTimesUseSeoulEvenWhenJvmUsesUtc() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime before = LocalDateTime.now(SERVICE_ZONE);
            Users admin = Users.builder().oauthId("admin").nickname("관리자").build();
            Certification certification = Certification.builder()
                    .user(admin).semester("2026-2").imageKey("test.png").build();

            ReflectionTestUtils.invokeMethod(certification, "onCreate");
            certification.approve(admin, null, before.plusDays(1));
            LocalDateTime approvedAt = certification.getReviewedAt();
            certification.reject(admin, "재제출해주세요.");
            LocalDateTime after = LocalDateTime.now(SERVICE_ZONE);

            assertThat(certification.getCreatedAt()).isBetween(before, after);
            assertThat(approvedAt).isBetween(before, after);
            assertThat(certification.getReviewedAt()).isBetween(before, after);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }
}
