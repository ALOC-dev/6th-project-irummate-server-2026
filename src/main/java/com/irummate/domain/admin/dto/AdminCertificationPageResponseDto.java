package com.irummate.domain.admin.dto;

import java.util.List;

public record AdminCertificationPageResponseDto(
        List<AdminCertificationResponseDto> certifications,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
