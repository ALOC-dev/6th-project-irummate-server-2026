package com.irummate.domain.matching.controller;

import com.irummate.domain.matching.dto.ConfirmedContactResponseDto;
import com.irummate.domain.matching.dto.MatchConfirmRequestDto;
import com.irummate.domain.matching.dto.MatchingRequestDto;
import com.irummate.domain.matching.dto.MatchingResponseDto;
import com.irummate.domain.matching.service.MatchingService;
import com.irummate.global.aop.AuthRole;
import com.irummate.global.aop.RequiresCertification;
import com.irummate.global.aop.RequiresAuth;
import com.irummate.global.aop.RequiresMatchDate;
import com.irummate.global.aop.RequiresSurvey;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import com.irummate.global.util.HashIdsUtils;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {


    private final MatchingService matchingService;
    private final HashIdsUtils hashIdsUtils;

    @Autowired
    public MatchingController(MatchingService matchingService,
                              HashIdsUtils hashIdsUtils){
        this.matchingService = matchingService;
        this.hashIdsUtils = hashIdsUtils;
    }


    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "매칭 카드 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없거나, 설문 작성 또는 이번 학기 기숙사 인증이 완료되지 않았거나, 매칭 기간이 아닙니다."),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없거나 매칭 일정이 설정되어 있지 않습니다.")
    })
    @RequiresAuth(roles = AuthRole.USER)
    @RequiresMatchDate
    @RequiresSurvey
    @RequiresCertification
    @GetMapping("/status")
    public ResponseEntity<GlobalApiResponse<?>> getMatchingStatus(
            @AuthenticationPrincipal Long userId
    ){
        List<MatchingResponseDto> responseDtos = matchingService.getMatchingStatus(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.multiSuccess(
                        HttpStatus.OK,
                        "매칭 카드 조회 성공",
                        responseDtos,
                        responseDtos.size()
                )
        );
    }


    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "오늘의 매칭 추천 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없거나, 설문 작성 또는 이번 학기 기숙사 인증이 완료되지 않았거나, 매칭 기간이 아닙니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 설문 정보를 찾을 수 없거나, 매칭 일정이 설정되지 않았거나, 추천 가능한 상대가 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 최종 매칭되었거나, 오늘 이미 추천을 완료했거나, 다른 매칭 추천 요청을 처리 중입니다.")
    })
    @RequiresAuth(roles = AuthRole.USER)
    @RequiresSurvey
    @RequiresCertification
    @RequiresMatchDate
    @PostMapping("/match")
    public ResponseEntity<GlobalApiResponse<?>> match(
            @AuthenticationPrincipal Long userId
    ){
        matchingService.match(userId);

        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "매칭 성공", null));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "하트 전달 또는 거절 처리 성공"),
            @ApiResponse(responseCode = "400", description = "필수 입력값이나 상대방 식별자가 유효하지 않습니다. 요청 상태는 하트 전달(HEART) 또는 거절(REJECT)만 가능합니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없거나, 설문 작성 또는 이번 학기 기숙사 인증이 완료되지 않았거나, 매칭 기간이 아닙니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 매칭 요청을 찾을 수 없거나 매칭 일정이 설정되어 있지 않습니다."),
            @ApiResponse(responseCode = "409", description = "현재 매칭 상태에서는 하트 전달 또는 거절을 할 수 없습니다.")
    })
    @RequiresAuth(roles = AuthRole.USER)
    @RequiresSurvey
    @RequiresCertification
    @RequiresMatchDate
    @PatchMapping("/requests")
    public ResponseEntity<GlobalApiResponse<?>> sendHeartOrRejectToReceiver(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MatchingRequestDto requestDto
    ){

        if(requestDto.getMatchStatus().equals("REJECT")){
            matchingService.reject(userId, hashIdsUtils.decode(requestDto.getReceiverId()));
            return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK,"하트 거절 성공",null));
        }

        if(requestDto.getMatchStatus().equals("HEART")){
            matchingService.heart(userId, hashIdsUtils.decode(requestDto.getReceiverId()));
            return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "하트 전달 성공", null));
        }


        throw new BusinessException(ErrorCode.BAD_REQUEST, "잘못된 요청입니다. 요청은 HEART/REJECT만 가능합니다.");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최종 매칭 확정 의사 전달 성공. 양쪽 모두 확정하면 최종 매칭이 완료됩니다."),
            @ApiResponse(responseCode = "400", description = "필수 입력값이나 상대방 식별자가 유효하지 않거나 자기 자신에게 확정을 요청했습니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없거나 설문 작성 또는 이번 학기 기숙사 인증이 완료되지 않았습니다."),
            @ApiResponse(responseCode = "404", description = "사용자, 설문 정보 또는 매칭 요청을 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 최종 매칭된 사용자가 있거나 현재 상태에서 확정할 수 없습니다.")
    })
    @RequiresAuth(roles = AuthRole.USER)
    @RequiresSurvey
    @RequiresCertification
    @PatchMapping("/requests/confirm")
    public ResponseEntity<GlobalApiResponse<?>> sendConfirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MatchConfirmRequestDto requestDto
    ){
        matchingService.confirm(userId, hashIdsUtils.decode(requestDto.getReceiverId()));
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "매칭 확정 전달 성공", null));
    }

    @RequiresAuth(roles = AuthRole.USER)
    @RequiresSurvey
    @RequiresCertification
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최종 확정 상대의 연락처 조회 성공"),
            @ApiResponse(responseCode = "400", description = "상대방 식별자가 유효하지 않거나 자기 자신의 연락처를 요청했습니다."),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없거나, 설문 작성 또는 이번 학기 기숙사 인증이 완료되지 않았거나, 서로 최종 확정된 관계가 아니거나, 상대방의 필수 정보가 없습니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 매칭 요청을 찾을 수 없습니다.")
    })
    @GetMapping("/requests/{receiverId}/contact")
    public ResponseEntity<GlobalApiResponse<?>> getConfirmedContact(
            @AuthenticationPrincipal Long userId,
            @PathVariable String receiverId
    ) {
        ConfirmedContactResponseDto responseDto = matchingService.getConfirmedContact(
                userId,
                hashIdsUtils.decode(receiverId)
        );

        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "확정 상대 연락처 조회 성공", responseDto));
    }

}
