package com.irummate.domain.calendar.controller;

import com.irummate.domain.calendar.dto.CalendarEventDto.Item;
import com.irummate.domain.calendar.dto.CalendarEventDto.Request;
import com.irummate.domain.calendar.service.CalendarEventService;
import com.irummate.domain.calendar.service.CalendarExtractionResultService;
import com.irummate.domain.calendar.service.CalendarExtractionResultService.ReviewItem;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dormitory-schedules")
@RequiredArgsConstructor
public class AdminCalendarController {
    private final CalendarEventService calendar;
    private final CalendarExtractionResultService extractions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GlobalApiResponse<Item> create(@Valid @RequestBody Request request) {
        return GlobalApiResponse.success(HttpStatus.CREATED, "일정 등록 완료", calendar.create(request));
    }

    @PatchMapping("/{eventId}")
    public GlobalApiResponse<Item> update(@PathVariable long eventId, @RequestBody ObjectNode patch) {
        return GlobalApiResponse.success(HttpStatus.OK, "일정 수정 완료", calendar.update(eventId, patch));
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long eventId) {
        calendar.delete(eventId);
    }

    @GetMapping("/extractions")
    public GlobalApiResponse<ReviewItems> findReviews(
            @RequestParam(defaultValue = "0") long afterId) {
        return GlobalApiResponse.success(HttpStatus.OK, "일정 검토 목록 조회 완료",
                new ReviewItems(extractions.findReviews(afterId)));
    }

    @PatchMapping("/extractions/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveReview(@PathVariable long jobId) {
        extractions.resolveReview(jobId);
    }

    public record ReviewItems(List<ReviewItem> items) {}
}
