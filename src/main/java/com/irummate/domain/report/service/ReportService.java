package com.irummate.domain.report.service;

import com.irummate.domain.report.dto.ReportRequestDto;
import com.irummate.domain.report.dto.ReportResponseDto;
import com.irummate.domain.report.dto.ReportsResponseDto;
import com.irummate.domain.report.entity.Report;
import com.irummate.domain.report.repository.ReportRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final ReportRepository reportRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;

    // 제출할 때마다 한 건을 저장한다. 차단한 상대도 신고할 수 있다.
    @Transactional
    public void report(Long reporterId, Long reportedId, ReportRequestDto request) {
        if (reporterId == null || reporterId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateId(reportedId);
        if (reporterId.equals(reportedId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        Users reporter = getUser(reporterId);
        Users reported = getUser(reportedId);
        reportRepository.save(new Report(reporter, reported, request.sourceType(), request.reason()));
    }

    // 관리자 신고 목록을 최신순으로 조회한다.
    public ReportsResponseDto findReports(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var result = reportRepository.findReports(PageRequest.of(page, size));
        var content = result.getContent().stream().map(this::toResponse).toList();
        return new ReportsResponseDto(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public ReportResponseDto findReport(Long reportId) {
        validateId(reportId);
        return reportRepository.findReport(reportId).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    // 외부 응답에서 신고 PK와 회원 PK를 Hashids로 변환한다.
    private ReportResponseDto toResponse(ReportRepository.ReportView report) {
        return new ReportResponseDto(hashIdsUtils.encode(report.getReportId()),
                hashIdsUtils.encode(report.getReporterId()), report.getReporterNickname(),
                hashIdsUtils.encode(report.getReportedId()), report.getReportedNickname(),
                report.getSourceType(), report.getReason(), report.getCreatedAt());
    }

    private Users getUser(Long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
