package com.irummate.domain.certification.service;

import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificationLifecycleService {

    private final CertificationRepository certificationRepository;

    @Transactional
    public int expirePreviousSemesters() {
        return certificationRepository.expirePreviousSemesters(
                SemesterUtils.currentSemester(),
                List.of(
                        CertificationStatus.PENDING,
                        CertificationStatus.APPROVED
                ),
                CertificationStatus.EXPIRED
        );
    }
}
