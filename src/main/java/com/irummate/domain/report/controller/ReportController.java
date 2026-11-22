package com.irummate.domain.report.controller;

import com.irummate.domain.report.dto.ReportRequestDto;
import com.irummate.domain.report.dto.ReportResponseDto;
import com.irummate.domain.report.dto.ReportsResponseDto;
import com.irummate.domain.report.service.ReportService;
import com.irummate.global.response.GlobalApiResponse;
import com.irummate.global.util.HashIdsUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final HashIdsUtils hashIdsUtils;

    // 신고자는 JWT에서, 신고 대상은 URL의 Hashids에서 가져온다.
    @PostMapping("/api/users/{userId}/reports")
    public ResponseEntity<GlobalApiResponse<Void>> report(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable String userId,
            @Valid @RequestBody ReportRequestDto request) {
        reportService.report(currentUserId, hashIdsUtils.decode(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalApiResponse.success(HttpStatus.CREATED, "신고가 접수되었습니다.", null));
    }

    @GetMapping("/api/admin/reports")
    public ResponseEntity<GlobalApiResponse<ReportsResponseDto>> findReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK, "신고 목록 조회 성공", reportService.findReports(page, size)));
    }

    @GetMapping("/api/admin/reports/{reportId}")
    public ResponseEntity<GlobalApiResponse<ReportResponseDto>> findReport(@PathVariable String reportId) {
        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK, "신고 상세 조회 성공", reportService.findReport(hashIdsUtils.decode(reportId))));
    }

}
