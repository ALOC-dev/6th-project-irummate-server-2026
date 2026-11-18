package com.irummate.domain.certification.repository;

import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, Long> {

    // 공개 UUID로 인증 요청 조회
    Optional<Certification> findByPublicId(UUID publicId);

    Page<Certification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Certification> findAllByCertificationStatusOrderByCreatedAtDesc(
            CertificationStatus certificationStatus,
            Pageable pageable
    );

    Optional<Certification> findByUser_IdAndSemester(Long userId, String semester);

    List<Certification> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Certification> findTopByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndSemester(Long userId, String semester);

    void deleteAllByUser_Id(Long userId);



    // 지난 학기의 대기·승인 기록만 일괄 만료
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
    update Certification c
    set c.certificationStatus = :expiredStatus,
        c.version = c.version + 1
    where c.semester < :currentSemester
      and c.certificationStatus in :targetStatuses
    """)
    int expirePreviousSemesters(
            @Param("currentSemester") String currentSemester,
            @Param("targetStatuses") List<CertificationStatus> targetStatuses,
            @Param("expiredStatus") CertificationStatus expiredStatus
    );
}
