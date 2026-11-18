package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.RecruitmentBookmarkListResponseDto;
import com.irummate.domain.recruitment.service.RecruitmentBookmarkService;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증이 필요하거나 인증 정보가 유효하지 않습니다.", content = @Content),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 내부 오류입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
        @ApiResponse(responseCode = "503", description = "DB 연결을 사용할 수 없거나 요청이 몰려 처리할 수 없습니다. Retry-After 헤더의 초 단위 대기 시간 이후 재시도할 수 있습니다.", headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", example = "2")), content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
})
public class RecruitmentBookmarkController {

    private final RecruitmentBookmarkService recruitmentBookmarkService;

    // 즐겨찾기 등록. 이미 등록된 경우에도 성공
    @Operation(summary = "모집글 즐겨찾기 등록", description = "공개된 글은 모집 상태와 관계없이 등록할 수 있습니다. 중복 요청은 기존 기록과 등록 시각을 유지한 채 성공합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "즐겨찾기 등록 성공 메시지를 반환하며 data는 생략합니다. 이미 등록된 경우에도 동일하게 성공합니다."),
            @ApiResponse(responseCode = "400", description = "모집글 ID는 양수여야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 또는 모집글을 찾을 수 없거나 모집글이 숨김 상태입니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
    @PutMapping("/{recruitmentId}/bookmark")
    public ResponseEntity<GlobalApiResponse<Void>> addBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {
        recruitmentBookmarkService.addBookmark(userId, recruitmentId);
        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK, "즐겨찾기를 등록했습니다.", null));
    }

    // 즐겨찾기 해제. 기록이 없는 경우에도 성공
    @Operation(summary = "모집글 즐겨찾기 해제", description = "본인의 즐겨찾기만 삭제합니다. 숨김 글도 해제할 수 있으며 모집글이나 즐겨찾기 기록이 없어도 성공합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "즐겨찾기 해제 성공 메시지를 반환하며 data는 생략합니다. 이미 해제된 경우에도 동일하게 성공합니다."),
            @ApiResponse(responseCode = "400", description = "모집글 ID는 양수여야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
    @DeleteMapping("/{recruitmentId}/bookmark")
    public ResponseEntity<GlobalApiResponse<Void>> removeBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recruitmentId
    ) {
        recruitmentBookmarkService.removeBookmark(userId, recruitmentId);
        return ResponseEntity.ok(GlobalApiResponse.success(
                HttpStatus.OK, "즐겨찾기를 해제했습니다.", null));
    }

    // 내 즐겨찾기 목록. 등록 시각과 즐겨찾기 기록 ID 기준의 커서 조회
    @Operation(summary = "내 모집글 즐겨찾기 목록 조회", description = "숨김 글을 제외한 내 즐겨찾기를 등록 시각 내림차순·즐겨찾기 기록 ID 내림차순으로 조회합니다. 다음 조회에는 nextCursor.bookmarkedAt과 id를 cursorBookmarkedAt과 cursorId로 함께 전달하며 첫 조회에서는 커서를 생략합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "모집글 목록과 내 참여 상태, 참여 가능 여부, bookmarked=true, hasNext, nextCursor를 반환합니다. 결과가 없으면 items는 빈 배열이고 다음 목록이 없으면 nextCursor는 null입니다. size는 이번에 반환한 글 개수입니다."),
            @ApiResponse(responseCode = "400", description = "커서 또는 size의 형식이 잘못되었거나 커서 시각과 ID를 함께 전달하지 않았습니다. cursorId는 양수, size는 1~50이어야 합니다.", content = @Content(schema = @Schema(implementation = GlobalApiResponse.class)))
    })
    @GetMapping("/bookmarks/me")
    public ResponseEntity<GlobalApiResponse<RecruitmentBookmarkListResponseDto>> getMyBookmarks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorBookmarkedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") int size
    ) {
        RecruitmentBookmarkListResponseDto response = recruitmentBookmarkService.getMyBookmarks(
                userId, cursorBookmarkedAt, cursorId, size);
        return ResponseEntity.ok(GlobalApiResponse.multiSuccess(
                HttpStatus.OK, "즐겨찾기 목록 조회 성공", response, response.getItems().size()));
    }
}
