package com.irummate.domain.admin.controller;

import com.irummate.domain.admin.dto.AdminUserDetailResponseDto;
import com.irummate.domain.admin.dto.AdminUserResponseDto;
import com.irummate.domain.admin.dto.AdminUsersResponseDto;
import com.irummate.domain.admin.service.AdminUserService;
import com.irummate.global.aop.AuthRole;
import com.irummate.global.aop.RequiresAuth;
import com.irummate.global.response.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminUsersResponseDto>> getUsers(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminUsersResponseDto responseDto = adminUserService.getUsers(page, size);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "회원 목록 조회 성공", responseDto)
        );
    }

    @GetMapping("/{userId}")
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminUserDetailResponseDto>> getUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String userId
    ) {
        AdminUserDetailResponseDto responseDto = adminUserService.getUser(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "회원 상세 조회 성공", responseDto)
        );
    }

    @PatchMapping("/{userId}/ban")
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminUserResponseDto>> banUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String userId
    ) {
        AdminUserResponseDto responseDto = adminUserService.banUser(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "회원 정지 성공", responseDto)
        );
    }

    @PatchMapping("/{userId}/unban")
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminUserResponseDto>> unbanUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable String userId
    ) {
        AdminUserResponseDto responseDto = adminUserService.unbanUser(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "회원 정지 해제 성공", responseDto)
        );
    }
}
