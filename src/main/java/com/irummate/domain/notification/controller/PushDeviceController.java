package com.irummate.domain.notification.controller;

import com.irummate.domain.notification.dto.PushDeviceRequest;
import com.irummate.domain.notification.service.PushDeviceService;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
public class PushDeviceController {
    private final PushDeviceService devices;

    @PutMapping("/{installationId}")
    public GlobalApiResponse<Void> register(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                            @PathVariable UUID installationId, @Valid @RequestBody PushDeviceRequest request) {
        devices.register(authorization, installationId, request);
        return GlobalApiResponse.success(HttpStatus.OK, "푸시 기기 등록 성공", null);
    }

    @DeleteMapping("/{installationId}")
    public GlobalApiResponse<Void> disable(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           @PathVariable UUID installationId) {
        devices.disable(authorization, installationId);
        return GlobalApiResponse.success(HttpStatus.OK, "푸시 기기 해제 성공", null);
    }
}
