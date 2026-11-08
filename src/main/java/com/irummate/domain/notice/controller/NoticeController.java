package com.irummate.domain.notice.controller;

import com.irummate.domain.notice.dto.NoticesResponseDto;
import com.irummate.domain.notice.dto.NoticeIngestRequestDto;
import com.irummate.domain.notice.dto.NoticeIngestResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.irummate.domain.notice.service.NoticeService;
import com.irummate.global.response.GlobalApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping("/internal/notices")
    public ResponseEntity<GlobalApiResponse<NoticeIngestResponseDto>> ingest(
            @Valid @RequestBody NoticeIngestRequestDto requestDto) {
        NoticeIngestResponseDto responseDto = noticeService.ingest(requestDto);
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "공지사항 수집 성공", responseDto));
    }

    @GetMapping("/api/notices")
    public ResponseEntity<GlobalApiResponse<NoticesResponseDto>> findNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        NoticesResponseDto responseDto = noticeService.findNotices(page, size, keyword);
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "공지사항 조회 성공", responseDto));
    }
}
