package com.irummate.domain.calendar.controller;

import com.irummate.domain.calendar.dto.CalendarEventDto.Item;
import com.irummate.domain.calendar.service.CalendarEventService;
import com.irummate.global.response.GlobalApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dormitory-schedules")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarEventService calendar;

    @GetMapping
    public GlobalApiResponse<Items> find(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return GlobalApiResponse.success(HttpStatus.OK, "기숙사 일정 조회 완료", new Items(calendar.find(from, to)));
    }

    public record Items(List<Item> items) {}
}
