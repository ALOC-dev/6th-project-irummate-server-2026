package com.irummate.domain.admin.controller;

import com.irummate.domain.admin.dto.AdminCertificationRejectRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationApproveRequestDto;
import com.irummate.domain.admin.dto.AdminCertificationPageResponseDto;
import com.irummate.domain.admin.dto.AdminCertificationResponseDto;
import com.irummate.domain.admin.service.AdminCertificationService;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/certifications")
public class AdminCertificationController {

    private final AdminCertificationService adminCertificationService;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<AdminCertificationPageResponseDto>> getCertifications(
            @RequestParam(required = false) CertificationStatus status,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        var response = adminCertificationService.getCertifications(status, semester, page, size);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "인증 요청 목록 조회 성공", response)
        );
    }

    @GetMapping("/{certificationId}")
    public ResponseEntity<GlobalApiResponse<AdminCertificationResponseDto>> getCertification(
            @PathVariable String certificationId
    ) {
        AdminCertificationResponseDto responseDto = adminCertificationService.getCertification(parseCertificationId(certificationId));

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "인증 요청 상세 조회 성공", responseDto)
        );
    }

    @PatchMapping("/{certificationId}/approve")
    public ResponseEntity<GlobalApiResponse<AdminCertificationResponseDto>> approveCertification(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String certificationId,
            @Valid @RequestBody AdminCertificationApproveRequestDto requestDto
    ) {
        AdminCertificationResponseDto responseDto =
                adminCertificationService.approveCertification(adminUserId, parseCertificationId(certificationId), requestDto);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "인증 요청 승인 성공", responseDto)
        );
    }

    @PatchMapping("/{certificationId}/reject")
    public ResponseEntity<GlobalApiResponse<AdminCertificationResponseDto>> rejectCertification(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String certificationId,
            @Valid @RequestBody AdminCertificationRejectRequestDto requestDto
    ) {
        AdminCertificationResponseDto responseDto =
                adminCertificationService.rejectCertification(adminUserId, parseCertificationId(certificationId), requestDto);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "인증 요청 거절 성공", responseDto)
        );
    }

    private UUID parseCertificationId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // UUID.fromString은 일부 축약 표기도 허용하므로 표준 문자열과 비교한다.
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 값이나 형식이 올바르지 않습니다.");
    }
}
