package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public void leaveRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {

    }


    // 참여자 목록 조회(익명)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 참여자 목록 조회 기능은 미구현이며 응답 본문 없이 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @GetMapping("/{recruitmentId}/participants")
    public void getRecruitmentParticipants(
            @PathVariable Long recruitmentId
    ) {}


    // 내 참여상태 조회
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 내 참여 상태 조회 기능은 미구현이며 응답 본문 없이 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @GetMapping("/{recruitmentId}/participants/me")
    public void getMyParticipation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}

    // 참여자 강제 제외
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 참여자 강제 제외 기능은 미구현이며 응답 본문 없이 반환합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    @DeleteMapping("/{recruitmentId}/participants/{participantId}")
    public void removeParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @PathVariable Long participantId
    ) {}

}
