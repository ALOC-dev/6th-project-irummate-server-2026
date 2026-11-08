package com.irummate.domain.block.dto;

import java.util.List;

// 내 차단 목록 화면의 사용자 목록과 페이지 이동에 필요한 정보
public record UserBlocksResponseDto(List<BlockedUserResponseDto> content, int page, int size,
                                    long totalElements, int totalPages) { }
