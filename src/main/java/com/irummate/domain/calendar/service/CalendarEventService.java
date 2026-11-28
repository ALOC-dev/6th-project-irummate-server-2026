package com.irummate.domain.calendar.service;

import com.irummate.domain.calendar.dto.CalendarEventDto.Item;
import com.irummate.domain.calendar.dto.CalendarEventDto.Request;
import com.irummate.domain.calendar.entity.CalendarEvent;
import com.irummate.domain.calendar.entity.CalendarEvent.Details;
import com.irummate.domain.calendar.entity.CalendarEvent.SourceType;
import com.irummate.domain.calendar.repository.CalendarEventRepository;
import com.irummate.domain.notice.entity.DormitoryNotice;
import com.irummate.domain.notice.repository.NoticeRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CalendarEventService {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "title", "category", "audience", "description", "allDay", "startAt", "endAt", "noticeId");

    private final CalendarEventRepository events;
    private final NoticeRepository notices;
    private final Clock clock;
    private final ObjectMapper json;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<Item> find(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0 || days > 366 || from.getYear() < 1 || to.getYear() > 9999) {
            throw invalid("조회 범위는 시작일 포함, 종료일 제외이며 최대 366일입니다.");
        }
        return events.findOverlapping(from.atStartOfDay(SEOUL).toInstant(), to.atStartOfDay(SEOUL).toInstant())
                .stream().map(this::toItem).toList();
    }

    @Transactional
    public Item create(Request request) {
        Details details = detailsOf(request);
        var event = new CalendarEvent(SourceType.MANUAL, findNotice(request.noticeId()), details, clock.instant());
        return toItem(events.save(event));
    }

    @Transactional
    public Item update(long id, ObjectNode patch) {
        CalendarEvent event = active(id);
        ObjectNode merged = json.valueToTree(toRequest(event));
        int edited = 0;
        for (String field : EDITABLE_FIELDS) {
            if (patch.has(field)) {
                merged.set(field, patch.get(field));
                edited++;
            }
        }
        if (edited == 0 || edited != patch.size()) {
            throw invalid("수정 가능한 일정 필드만 입력해주세요.");
        }

        Request request;
        try {
            request = json.treeToValue(merged, Request.class);
        } catch (JacksonException e) {
            throw invalid("일정 필드의 형식이 올바르지 않습니다.");
        }
        if (!validator.validate(request).isEmpty()) {
            throw invalid("필수 값과 최대 길이를 확인해주세요.");
        }
        Details details = detailsOf(request);
        if (event.getSourceType() == SourceType.AI
                && !event.getNotice().getId().equals(request.noticeId())) {
            throw invalid("자동 등록 일정의 원문 공지는 변경할 수 없습니다.");
        }
        event.update(findNotice(request.noticeId()), details, clock.instant());
        return toItem(event);
    }

    @Transactional
    public void delete(long id) {
        active(id).delete(clock.instant());
    }

    private CalendarEvent active(long id) {
        return events.lockActive(id).orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }

    private DormitoryNotice findNotice(Long id) {
        return id == null ? null : notices.findById(id)
                .orElseThrow(() -> invalid("연결할 기숙사 공지를 찾을 수 없습니다."));
    }

    private static Details detailsOf(Request request) {
        Instant start = request.startAt().toInstant();
        Instant end = request.endAt() == null ? null : request.endAt().toInstant();

        if (request.allDay()) {
            var localStart = start.atZone(SEOUL);
            if (!localStart.toLocalTime().equals(LocalTime.MIDNIGHT)
                    || (end != null && !end.atZone(SEOUL).toLocalTime().equals(LocalTime.MIDNIGHT))) {
                throw invalid("종일 일정의 시작과 종료는 한국 시간 자정이어야 합니다.");
            }
            if (end == null) {
                end = localStart.plusDays(1).toInstant();
            }
        }
        if (end != null && !end.isAfter(start)) {
            throw invalid("일정 종료는 시작보다 뒤여야 합니다.");
        }
        return new Details(request.title().strip(), request.category(), request.audience(), request.description(),
                request.allDay(), start, end);
    }

    private Item toItem(CalendarEvent event) {
        return Item.from(event, toRequest(event));
    }

    private Request toRequest(CalendarEvent event) {
        Details d = event.getDetails();
        return new Request(d.title(), d.category(), d.audience(), d.description(), d.allDay(),
                inSeoul(d.startAt()), inSeoul(d.endAt()),
                event.getNotice() == null ? null : event.getNotice().getId());
    }

    private static OffsetDateTime inSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}
