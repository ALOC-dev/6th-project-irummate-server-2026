package com.irummate.domain.certification.controller;

import com.irummate.domain.certification.dto.CertificationRequestDto;
import com.irummate.domain.certification.dto.CertificationPresignRequestDto;
import com.irummate.domain.certification.dto.CertificationPresignResponseDto;
import com.irummate.domain.certification.dto.CertificationResponseDto;
import com.irummate.domain.certification.dto.CertificationStatusResponseDto;
import com.irummate.domain.certification.service.CertificationPresignService;
import com.irummate.domain.certification.service.CertificationService;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final CertificationPresignService certificationPresignService;


    @Autowired
    public CertificationController(CertificationService certificationService,
                                   CertificationPresignService certificationPresignService){
        this.certificationPresignService = certificationPresignService;
        this.certificationService = certificationService;
    }


    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse<CertificationStatusResponseDto>> getLatestCertificationStatus(
            @AuthenticationPrincipal Long userId
    ) {
        CertificationStatusResponseDto response = certificationService.getLatestCertificationStatus(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "인증 요청 상태 조회 성공", response)
        );
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<GlobalApiResponse<CertificationPresignResponseDto>> createUploadUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CertificationPresignRequestDto requestDto
    ) {
        CertificationPresignResponseDto response = certificationPresignService.createUploadUrl(userId, requestDto);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "업로드 URL 발급 성공", response)
        );
    }

    @PostMapping
    public ResponseEntity<GlobalApiResponse<CertificationResponseDto>> createCertification(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CertificationRequestDto requestDto
    ) {
        CertificationResponseDto response = certificationService.createCertification(userId, requestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalApiResponse.success(
                        HttpStatus.CREATED,
                        "인증 요청이 제출되었습니다.",
                        response
                ));
    }
}
