package com.irummate.domain.admin.dto;

import java.util.List;

public record AdminMonitoringUsersResponseDto(
        List<AdminMonitoringUserResponseDto> users
) {
}
