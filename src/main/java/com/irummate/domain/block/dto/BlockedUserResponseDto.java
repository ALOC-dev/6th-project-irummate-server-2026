package com.irummate.domain.block.dto;

import java.time.LocalDateTime;

// 차단 목록의 사용자 한 명에 대한 응답
public record BlockedUserResponseDto(String userId, String nickname, LocalDateTime createdAt) { }
