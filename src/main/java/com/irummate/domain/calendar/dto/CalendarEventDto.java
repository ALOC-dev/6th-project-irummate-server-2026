package com.irummate.domain.calendar.dto;

import com.irummate.domain.calendar.entity.CalendarEvent;
import com.irummate.domain.calendar.entity.CalendarEvent.Category;
import com.irummate.domain.calendar.entity.CalendarEvent.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class CalendarEventDto {
    private CalendarEventDto() {}

    public record Request(
            @NotBlank @Size(max = 200) String title,
            @NotNull Category category,
            @Size(max = 200) String audience,
            @Size(max = 5000) String description,
            @NotNull Boolean allDay,
            @NotNull OffsetDateTime startAt,
            OffsetDateTime endAt,
            @Positive Long noticeId
    ) {}

    public record Item(long id, String title, Category category, String audience, String description, boolean allDay,
                       OffsetDateTime startAt, OffsetDateTime endAt, SourceType sourceType, Long noticeId,
                       String noticeUrl) {
        public static Item from(CalendarEvent event, Request data) {
            return new Item(event.getId(), data.title(), data.category(), data.audience(), data.description(),
                    data.allDay(), data.startAt(), data.endAt(), event.getSourceType(), data.noticeId(),
                    event.getNotice() == null ? null : event.getNotice().getUrl());
        }
    }
}
