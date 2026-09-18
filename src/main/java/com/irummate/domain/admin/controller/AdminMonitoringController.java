package com.irummate.domain.admin.controller;

import com.irummate.domain.admin.dto.AdminMonitoringSummaryResponseDto;
import com.irummate.domain.admin.dto.AdminMonitoringUsersResponseDto;
import com.irummate.domain.admin.service.AdminMonitoringService;
import com.irummate.global.aop.AuthRole;
import com.irummate.global.aop.RequiresAuth;
import com.irummate.global.response.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/monitoring")
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;

    @GetMapping("/summary")
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminMonitoringSummaryResponseDto>> getSummary() {
        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "Admin monitoring summary retrieved", adminMonitoringService.getSummary())
        );
    }

    @GetMapping("/users")
    @RequiresAuth(roles = AuthRole.ADMIN)
    public ResponseEntity<GlobalApiResponse<AdminMonitoringUsersResponseDto>> getUsers() {
        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "Admin monitoring users retrieved", adminMonitoringService.getUsers())
        );
    }
}
