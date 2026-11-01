package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.service.RecruitmentService;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentController {


    private final RecruitmentService recruitmentService;

    // 전체 게시글을 조회하는 api
    @GetMapping
    public ResponseEntity<GlobalApiResponse<?>> getRecruitments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){

        List<RecruitmentResponseDto> response = recruitmentService.getRecruitments(page, size);

        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK,
                "게시글 목록 조회 성공",
                response,
                response.size()
        ));
    }


    // 게시글 작성 api
    @PostMapping
    public ResponseEntity<GlobalApiResponse<?>> createRecruitment(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecruitmentCreateRequestDto request
            ) {

        RecruitmentCreateResponseDto response = recruitmentService.createRecruitment(userId, request);

        URI location = URI.create(
                "/api/recruitments/" + response.getRecruitmentId()
        );

        return ResponseEntity
                .created(location)
                .body(GlobalApiResponse.success(
                        HttpStatus.CREATED,
                        "게시글 작성 성공",
                        response
                ));
    }


    // 게시글 세부 조회 api
    @GetMapping("/{recruitmentId}")
    public ResponseEntity<GlobalApiResponse<?>> getRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ){

        RecruitmentDetailResponseDto response = recruitmentService.getRecruitment(userId, recruitmentId);

        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글 조회 성공",
                response
        ));
    }


    // 게시글 수정 api
    @PatchMapping("/{recruitmentId}")
    public void updateRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @Valid @RequestBody RecruitmentUpdateRequestDto request
    ) {




    }


    // 게시글 모집 수동 마감 api
    @PatchMapping("/{recruitmentId}/close")
    public void closeRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {

    }


    // 게시글 모집 재개 api
    @PatchMapping("/{recruitmentId}/reopen")
    public void reopenRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}


    // 게시글 모집 및 활동 취소 api
    @PatchMapping("/{recruitmentId}/cancel")
    public void cancelRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}


    // 게시글 활동 완료 api
    @PatchMapping("/{recruitmentId}/complete")
    public void completeRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}

}
