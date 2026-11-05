package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.recruitment.service.RecruitmentService;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 목록 조회 성공. 조회 결과가 없으면 빈 목록을 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @GetMapping
    public ResponseEntity<GlobalApiResponse<List<RecruitmentResponseDto>>> getRecruitments(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){

        List<RecruitmentResponseDto> response = recruitmentService.getRecruitments(userId, page, size);

        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK,
                "게시글 목록 조회 성공",
                response,
                response.size()
        ));
    }


    // 게시글 작성 api
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "모집글 작성 성공. 생성된 모집글 식별자와 위치를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "필수 입력값, 글자 수, 모집 인원, 예상 비용 또는 일정이 유효하지 않습니다. 모집 마감 일시는 활동 일시보다 이전이어야 합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.")
    })
    @PostMapping
    public ResponseEntity<GlobalApiResponse<RecruitmentCreateResponseDto>> createRecruitment(
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 상세 조회 성공. 본인 작성 여부, 참여 여부와 참여 가능 여부를 함께 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 모집글을 찾을 수 없습니다.")
    })
    @GetMapping("/{recruitmentId}")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> getRecruitment(
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 수정 성공"),
            @ApiResponse(responseCode = "400", description = "수정할 항목이 없거나 입력값이 유효하지 않습니다. 일정은 미래여야 하며 모집 마감은 활동 시작보다 빨라야 합니다. 정원은 현재 참여 인원 이상이어야 합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 모집글의 작성자만 수정할 수 있습니다."),
            @ApiResponse(responseCode = "404", description = "모집글이 없거나 비공개 상태입니다."),
            @ApiResponse(responseCode = "409", description = "취소 또는 완료된 모집글이거나 활동이 시작되어 수정할 수 없습니다.")
    })
    @PatchMapping("/{recruitmentId}")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> updateRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @Valid @RequestBody RecruitmentUpdateRequestDto request
    ) {

        RecruitmentDetailResponseDto response =
                recruitmentService.updateRecruitment(userId, recruitmentId, request);

        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글 수정에 성공했습니다.",
                response
        ));
    }


    // 게시글 모집 수동 마감 api
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 수동 마감 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 모집글의 작성자만 마감할 수 있습니다."),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "현재 모집 중인 게시글만 마감할 수 있습니다.")
    })
    @PatchMapping("/{recruitmentId}/close")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> closeRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {


        RecruitmentDetailResponseDto response = recruitmentService.closeRecruitment(userId, recruitmentId);

        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글 모집을 마감했습니다.",
                response
        ));


    }


    // 게시글 모집 재개 api
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 모집 재개 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 모집글의 작성자만 모집을 재개할 수 있습니다."),
            @ApiResponse(responseCode = "404", description = "모집글이 없거나 비공개 상태입니다."),
            @ApiResponse(responseCode = "409", description = "마감 상태가 아니거나, 활동이 시작되었거나, 모집 기한이 지났거나, 정원이 가득 차 모집을 재개할 수 없습니다.")
    })
    @PatchMapping("/{recruitmentId}/reopen")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> reopenRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {
        RecruitmentDetailResponseDto response = recruitmentService.reopenRecruitment(userId, recruitmentId);

        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글 모집 재개에 성공했습니다.",
                response
        ));
    }


    // 게시글 모집 및 활동 취소 api
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집 및 활동 취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 사유가 비어 있거나 100자를 초과했습니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 모집글의 작성자만 취소할 수 있습니다."),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 취소 또는 완료되었거나 활동이 시작되어 취소할 수 없습니다.")
    })
    @PatchMapping("/{recruitmentId}/cancel")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> cancelRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @Valid @RequestBody RecruitmentCancelRequestDto request
    ) {

        RecruitmentDetailResponseDto response =
                recruitmentService.cancelRecruitment(userId, recruitmentId, request);


        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글 모집 및 활동이 취소되었습니다.",
                response
        ));

    }


    // 게시글 활동 완료 api
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 활동 완료 처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 모집글의 작성자만 활동 완료 처리할 수 있습니다."),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 취소 또는 완료되었거나 활동 시작 시각 전이어서 완료 처리할 수 없습니다.")
    })
    @PatchMapping("/{recruitmentId}/complete")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> completeRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {

        RecruitmentDetailResponseDto response = recruitmentService.completeRecruitment(userId, recruitmentId);


        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "게시글이 활동 완료 처리되었습니다.",
                response
        ));
    }

}
