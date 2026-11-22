package com.irummate.domain.report.dto;

import java.util.List;

public record ReportsResponseDto(List<ReportResponseDto> content, int page, int size,
                                 long totalElements, int totalPages) { }
