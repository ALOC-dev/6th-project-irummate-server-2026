package com.irummate.domain.block.controller;

import com.irummate.domain.block.dto.BlockStatusResponseDto;
import com.irummate.domain.block.dto.UserBlocksResponseDto;
import com.irummate.domain.block.service.UserBlockService;
import com.irummate.global.aop.RequiresAuth;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// JWT 인증 사용자의 차단 관리 API다. URL의 userId는 Hashids 식별자다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserBlockController {
    private final UserBlockService userBlockService;
    private final HashIdsUtils hashIdsUtils;

    @RequiresAuth
    // 유저 차단
    @PostMapping("/{userId}/block")
    public ResponseEntity<GlobalApiResponse<Void>> block(@AuthenticationPrincipal Long currentUserId,
                                                        @PathVariable String userId) {
        // 차단 실행자는 요청 값이 아닌 JWT principal에서 가져온다.
        userBlockService.block(currentUserId, hashIdsUtils.decode(userId));
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "사용자를 차단했습니다.", null));
    }

    @RequiresAuth
    // 차단 해제
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<GlobalApiResponse<Void>> unblock(@AuthenticationPrincipal Long currentUserId,
                                                          @PathVariable String userId) {
        userBlockService.unblock(currentUserId, hashIdsUtils.decode(userId));
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "사용자 차단을 해제했습니다.", null));
    }

    @RequiresAuth
    // 내 차단 유저 목록 불러오기
    @GetMapping("/blocks")
    public ResponseEntity<GlobalApiResponse<UserBlocksResponseDto>> findMyBlocks(
            @AuthenticationPrincipal Long currentUserId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        var result = userBlockService.findMyBlocks(currentUserId, parseInteger(page), parseInteger(size));
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "차단 목록 조회 성공", result));
    }

    @RequiresAuth
    // 차단/해제 버튼 표시를 결정할 때 사용
    @GetMapping("/{userId}/block-status")
    public ResponseEntity<GlobalApiResponse<BlockStatusResponseDto>> getBlockStatus(
            @AuthenticationPrincipal Long currentUserId, @PathVariable String userId) {
        var result = new BlockStatusResponseDto(
                userBlockService.getMyBlockStatus(currentUserId, hashIdsUtils.decode(userId)));
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "차단 여부 조회 성공", result));
    }

    // 공통 예외 처리기의 기존 API 동작을 바꾸지 않고 형식 오류를 400으로 변환한다.
    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
