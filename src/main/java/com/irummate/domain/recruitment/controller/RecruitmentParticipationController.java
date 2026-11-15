package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import jakarta.validation.Valid;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentParticipationController {


    private final RecruitmentParticipationService recruitmentParticipationService;

    // 게시글에 참여
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 참여 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "자신이 작성한 모집글에는 참여할 수 없습니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 모집글을 찾을 수 없거나 모집글이 비공개 상태입니다."),
            @ApiResponse(responseCode = "409", description = "참여 이력이 있거나, 모집 중이 아니거나, 마감 시간이 지났거나, 정원이 가득 찼습니다.")
    })
    @PostMapping("/{recruitmentId}/participants")
    public ResponseEntity<GlobalApiResponse<?>> joinRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ){

        recruitmentParticipationService.joinRecruitment(userId, recruitmentId);


        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "모임에 참여하였습니다.",
                null
        ));
    }


    // 참여 취소
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 참여 취소 기능은 미구현이며 응답 본문 없이 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @DeleteMapping("/{recruitmentId}/participants/me")
    public ResponseEntity<GlobalApiResponse<RecruitmentLeaveResponseDto>> leaveRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {
        RecruitmentLeaveResponseDto response = recruitmentParticipationService.leaveRecruitment(userId,recruitmentId);

        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "참여 취소에 성공했습니다.",
                response
        ));

    }


    // 로그인 사용자의 전체 모집글 참여 이력 조회 (현재 참여·자진 나가기·강제 제외 포함)
    @GetMapping("/participations/me")
    public ResponseEntity<GlobalApiResponse<List<RecruitmentResponseDto>>> getMyParticipations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        List<RecruitmentResponseDto> myParticipants = recruitmentParticipationService
                .getMyParticipation(userId, page, size);


        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK,
                "참여 이력 조회에 성공했습니다.",
                myParticipants,
                myParticipants.size()
        ));
    }

    // 참여자 목록 조회(익명)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 참여 중인 사용자 목록. 작성자가 맨 앞이며, 사용자 ID는 차단·신고·강제 제외에 쓰는 공개 ID입니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "404", description = "모집글이 없거나 비공개 상태입니다.")
    })
    @GetMapping("/{recruitmentId}/participants")
    public ResponseEntity<GlobalApiResponse<RecruitmentParticipantsResponseDto>> getRecruitmentParticipants(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {


        RecruitmentParticipantsResponseDto response = recruitmentParticipationService
                .getRecruitmentParticipants(userId, recruitmentId);


        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK,
                "참여자 목록 조회에 성공했습니다.",
                response,
                response.getParticipants().size()
        ));

    }


    // 참여자 강제 제외
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "참여자 강제 제외 및 변경된 인원·모집 상태 반환"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @PostMapping("/{recruitmentId}/participants/remove")
    public ResponseEntity<GlobalApiResponse<RecruitmentParticipantRemoveResponseDto>> removeParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @Valid @RequestBody RecruitmentParticipantRemoveRequestDto request
    ) {

        RecruitmentParticipantRemoveResponseDto response = recruitmentParticipationService.removeParticipant(
                userId,
                recruitmentId,
                request
        );


        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "참여자 추방에 성공했습니다.",
                response
        ));
    }

}
