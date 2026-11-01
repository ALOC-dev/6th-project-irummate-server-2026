package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import com.irummate.global.response.GlobalApiResponse;
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
    @DeleteMapping("/{recruitmentId}/participants/me")
    public void leaveRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}


    // 참여자 목록 조회(익명)
    @GetMapping("/{recruitmentId}/participants")
    public void getRecruitmentParticipants(
            @PathVariable Long recruitmentId
    ) {}


    // 내 참여상태 조회
    @GetMapping("/{recruitmentId}/participants/me")
    public void getMyParticipation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {}

    // 참여자 강제 제외
    @DeleteMapping("/{recruitmentId}/participants/{participantId}")
    public void removeParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId,
            @PathVariable Long participantId
    ) {}

}
