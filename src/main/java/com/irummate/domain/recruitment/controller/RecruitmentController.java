package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.service.RecruitmentService;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;


@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증이 필요하거나 인증 정보가 유효하지 않습니다.", content = @Content),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 내부 오류입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
        @ApiResponse(responseCode = "503", description = "DB 연결을 사용할 수 없거나 요청이 몰려 처리할 수 없습니다. Retry-After 헤더의 초 단위 대기 시간 이후 재시도할 수 있습니다.", headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", example = "2")), content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
})
public class RecruitmentController {


    private final RecruitmentService recruitmentService;

    // 전체 또는 카테고리별 게시글을 최신순·마감 임박순으로 조회하는 api
    @Operation(summary = "모집글 목록 조회", description = "기본 LATEST는 최신순입니다. CLOSING_SOON은 공개된 모집 중 글에서 마감까지 24시간 이내 또는 작성자 포함 정원 충족률 80% 이상인 글을 마감 시각 오름차순·ID 내림차순으로 조회합니다. 최신순은 cursorCreatedAt, 마감 임박순은 cursorDeadlineAt과 cursorId를 사용하며 첫 조회에서는 커서를 모두 생략합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 목록과 participating, joinable, bookmarked, hasNext, nextCursor를 반환합니다. size는 이번에 반환한 글 개수이며 전체 건수가 아닙니다. 결과가 없으면 items는 빈 배열이고 다음 목록이 없으면 nextCursor는 null입니다."),
            @ApiResponse(responseCode = "400", description = "카테고리·정렬·커서·size의 형식이 잘못되었거나, 정렬에 맞지 않는 커서를 전달했거나, 커서 시각과 ID를 함께 전달하지 않았습니다. cursorId는 양수, size는 1~50이어야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<GlobalApiResponse<RecruitmentListResponseDto>> getRecruitments(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) RecruitmentCategory category,
            @RequestParam(defaultValue = "LATEST") RecruitmentSort sort,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorDeadlineAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") int size
    ){

        RecruitmentListResponseDto response = recruitmentService.getRecruitments(
                userId, category, sort, cursorCreatedAt, cursorDeadlineAt, cursorId, size);

        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK,
                "게시글 목록 조회 성공",
                response,
                response.getItems().size()
        ));
    }


    // 게시글 작성 api
    @Operation(summary = "모집글 작성", description = "활동 일시는 현재 시각으로부터 최소 1시간 이후여야 합니다. 모집 마감은 서버에서 활동 30분 전으로 계산하며 deadlineAt 요청값은 사용하지 않습니다. maxParticipants는 작성자를 제외한 모집 인원이며 작성자를 HOST로 등록합니다. 공동구매는 priceType과 amount가 필요하고 다른 카테고리에는 금액 정보를 전달하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "모집글 식별자, 초기 상태, 작성 시각을 반환하고 Location 헤더에 생성된 모집글의 상세 조회 경로를 제공합니다.", headers = @Header(name = "Location", description = "생성된 모집글 상세 조회 경로", schema = @Schema(type = "string", format = "uri-reference"))),
            @ApiResponse(responseCode = "400", description = "필수 항목, 제목·장소 길이, 모집 인원, 금액 또는 일정이 유효하지 않습니다. 활동 시작까지 최소 1시간이 남아 있어야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집글 상세 조회", description = "숨김 글은 작성자만 조회할 수 있습니다. participating은 MINE·JOINED·LEFT·REMOVED·NONE 중 하나이며, 본인이 강제 제외된 경우에만 removalReason을 제공합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 상세 정보와 내 참여 상태, 참여 가능 여부, 즐겨찾기 여부 및 공개 상태를 반환합니다."),
            @ApiResponse(responseCode = "404", description = "사용자 또는 모집글을 찾을 수 없거나, 작성자가 아닌 사용자가 숨김 글을 조회했습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집글 수정", description = "전달한 필드만 수정하고 생략하거나 null인 필드는 유지합니다. 활동 일시 변경 시 최소 1시간 이후로 설정해야 하며 마감도 활동 30분 전으로 다시 계산합니다. deadlineAt 요청값은 사용하지 않습니다. maxParticipants는 작성자를 제외한 인원이며 변경된 정원은 현재 참여 인원 이상이어야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정된 모집글의 상세 정보와 내 즐겨찾기 여부를 반환합니다. 정원 변경으로 FULL 마감 조건이 달라지면 모집 상태도 반영합니다."),
            @ApiResponse(responseCode = "400", description = "수정할 필드가 없거나 입력값·일정·금액이 유효하지 않습니다. 활동 일시를 변경하면 현재 시각으로부터 최소 1시간 이후여야 하며 정원은 현재 참여 인원 이상이어야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 OPEN·CLOSED 상태가 아니거나 활동이 시작되어 수정할 수 없습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집 수동 마감", description = "작성자가 OPEN 상태의 모집글을 수동 마감하며 closeReason은 MANUAL이 됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CLOSED로 변경된 모집글의 상세 정보와 내 즐겨찾기 여부를 반환합니다."),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 OPEN 상태가 아니어서 마감할 수 없습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집 재개", description = "작성자가 CLOSED 상태의 글을 재개합니다. 활동 시작 전이고 모집 기한이 남아 있으며 정원에 여유가 있어야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OPEN으로 변경된 모집글의 상세 정보와 내 즐겨찾기 여부를 반환합니다."),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 CLOSED 상태가 아니거나 활동이 시작되었거나 모집 기한이 지났거나 정원이 가득 찼습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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
    @Operation(summary = "모집 및 활동 취소", description = "작성자가 활동 시작 전 OPEN·CLOSED 상태의 글을 취소합니다. 모집 마감 시각이 지났어도 활동 시작 전이면 취소할 수 있으며 취소 사유는 필수입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CANCELLED로 변경된 모집글의 상세 정보와 취소 사유, 내 즐겨찾기 여부를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "취소 사유가 비어 있거나 100자를 초과했습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 OPEN·CLOSED 상태가 아니거나 활동이 시작되어 취소할 수 없습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
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


    // 활동 시작에 따른 게시판 종료 api
    @Operation(summary = "활동 시작에 따른 게시판 종료", description = "활동 시작 시각 이후 작성자가 OPEN·CLOSED 글을 ENDED로 변경합니다. ENDED는 활동 완료가 아니라 게시판의 모집·참여 관리 종료를 의미합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ENDED로 변경된 모집글의 상세 정보와 종료 시각, 내 즐겨찾기 여부를 반환합니다."),
            @ApiResponse(responseCode = "403", description = "모집글 작성자가 아닙니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "모집글을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "숨김 글이거나 OPEN·CLOSED 상태가 아니거나 활동 시작 시각 전이어서 종료할 수 없습니다. 다른 요청과의 잠금 충돌도 포함합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
    @PatchMapping("/{recruitmentId}/end")
    public ResponseEntity<GlobalApiResponse<RecruitmentDetailResponseDto>> endRecruitment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {

        RecruitmentDetailResponseDto response = recruitmentService.endRecruitment(userId, recruitmentId);


        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK,
                "활동 시작으로 게시판 처리가 종료되었습니다.",
                response
        ));
    }

}
