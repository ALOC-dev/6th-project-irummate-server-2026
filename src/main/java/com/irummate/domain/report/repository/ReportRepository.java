package com.irummate.domain.report.repository;

import com.irummate.domain.report.entity.Report;
import com.irummate.domain.report.entity.ReportSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // 회원 엔티티 전체 대신 관리자 응답에 필요한 컬럼만 조회한다.
    interface ReportView {
        Long getReportId();
        Long getReporterId();
        String getReporterNickname();
        Long getReportedId();
        String getReportedNickname();
        ReportSourceType getSourceType();
        String getReason();
        LocalDateTime getCreatedAt();
    }

    @Query(value = """
            SELECT r.id AS reportId, reporter.id AS reporterId, reporter.nickname AS reporterNickname,
                   reported.id AS reportedId, reported.nickname AS reportedNickname,
                   r.sourceType AS sourceType, r.reason AS reason, r.createdAt AS createdAt
            FROM Report r JOIN r.reporter reporter JOIN r.reported reported
            ORDER BY r.createdAt DESC, r.id DESC
            """, countQuery = "SELECT COUNT(r) FROM Report r")
    Page<ReportView> findReports(Pageable pageable);

    @Query("""
            SELECT r.id AS reportId, reporter.id AS reporterId, reporter.nickname AS reporterNickname,
                   reported.id AS reportedId, reported.nickname AS reportedNickname,
                   r.sourceType AS sourceType, r.reason AS reason, r.createdAt AS createdAt
            FROM Report r JOIN r.reporter reporter JOIN r.reported reported
            WHERE r.id = :reportId
            """)
    Optional<ReportView> findReport(@Param("reportId") Long reportId);
}
