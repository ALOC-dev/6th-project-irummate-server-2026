package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import jakarta.validation.Valid;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Operation;
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
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증이 필요하거나 인증 정보가 유효하지 않습니다.", content = @Content),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 내부 오류입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
        @ApiResponse(responseCode = "503", description = "DB 연결을 사용할 수 없거나 요청이 몰려 처리할 수 없습니다. Retry-After 헤더의 초 단위 대기 시간 이후 재시도할 수 있습니다.", headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", example = "2")), content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
})
public class RecruitmentParticipationController {


    private final RecruitmentParticipationService recruitmentParticipationService;

    // 게시글에 참여
    @Operation(summary = "모집글 참여", description = "공개된 모집 중 글에 참여합니다. 작성자는 별도로 참여할 수 없으며 자진 나가기·강제 제외를 포함해 참여 이력이 있으면 재참여할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "참여 기록과 참여 인원을 반영합니다. 정원이 차면 FULL 사유로 모집을 마감하며 성공 메시지만 반환하고 data는 생략합니다."),
            @ApiResponse(responseCode = "403", description = "자신이 작성한 모집글에는 참여할 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 또는 모집글을 찾을 수 없거나 모집글이 숨김 상태입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "참여 이력이 있거나 OPEN 상태가 아니거나 모집 기한이 지났거나 정원이 가득 찼습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집글 참여 취소", description = "작성자를 제외한 현재 참여자가 활동 시작 전 OPEN·CLOSED 글에서 나갑니다. 참여 기록은 LEFT로 유지하며 이후 재참여할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 참여 상태 LEFT, 나간 시각, 감소한 참여 인원·정원과 모집 상태, joinable=false를 반환합니다. FULL 마감 글은 기한과 공개 상태 등 재개 조건을 만족하면 자동으로 재개합니다."),
            @ApiResponse(responseCode = "404", description = "모집글 또는 본인의 참여 기록이 없거나 모집글이 숨김 상태입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "작성자는 참여 취소할 수 없습니다. 현재 참여 상태가 아니거나 OPEN·CLOSED 글이 아니거나 활동이 시작된 경우에도 거부합니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "내 모집글 참여 이력 조회", description = "작성한 글과 현재 참여·자진 나가기·강제 제외 이력을 참여 시각 내림차순·참여 기록 ID 내림차순으로 조회합니다. 다른 작성자의 숨김 글은 제목을 가리고 게시글 세부 정보를 생략합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "참여 이력 배열과 participating, joinable, bookmarked를 반환합니다. size는 이번에 반환한 이력 개수이며 결과가 없으면 size=0이고 공통 응답 정책에 따라 data는 생략합니다."),
            @ApiResponse(responseCode = "400", description = "page는 0 이상, size는 1~50이어야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
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

    // 참여자 목록 조회
    @Operation(summary = "모집글 참여자 목록 조회", description = "JOINED 상태만 참여 시각·참여 기록 ID 오름차순으로 조회합니다. 자진 나가기·강제 제외된 참여 기록은 제외하며 숨김 글은 작성자만 조회할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 인원·정원과 참여자 목록을 반환합니다. 각 참여자는 공개 userId(Hashids), 닉네임, 프로필 주소, HOST·MEMBER 역할, 참여 시각과 본인 여부를 포함합니다."),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없거나 작성자가 아닌 사용자가 숨김 글을 조회했습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집글 참여자 강제 제외", description = "작성자가 활동 시작 전 공개된 OPEN·CLOSED 글에서 참여자를 제외합니다. targetUserId는 참여자 목록의 공개 userId(Hashids)이며 제외 사유는 필수입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대상 공개 userId, REMOVED 상태, 제외 사유·시각과 변경된 참여 인원·정원·모집 상태를 반환합니다. FULL 마감 글은 재개 조건을 만족하면 자동으로 재개합니다."),
            @ApiResponse(responseCode = "400", description = "대상 공개 ID가 없거나 형식이 잘못되었거나 제외 사유가 비어 있거나 100자를 초과했습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글 또는 제외할 사용자의 참여 기록을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 작성자를 제외하려 하거나 대상이 현재 참여 중이 아니거나 OPEN·CLOSED 상태가 아니거나 활동이 시작되었습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
