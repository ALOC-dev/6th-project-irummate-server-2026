package com.irummate.domain.calendar.entity;

import com.irummate.domain.notice.entity.DormitoryNotice;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "calendar_events", indexes = {
        @Index(name = "idx_calendar_events_start", columnList = "start_at,id"),
        @Index(name = "idx_calendar_events_notice", columnList = "notice_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {
    public enum Category { APPLICATION, RESULT, PAYMENT, MOVE_IN, MOVE_OUT, MATCHING, OTHER }
    public enum SourceType { MANUAL, AI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private DormitoryNotice notice;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Embedded
    private Details details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CalendarEvent(SourceType sourceType, DormitoryNotice notice, Details details, Instant now) {
        this.sourceType = sourceType;
        this.createdAt = now;
        update(notice, details, now);
    }

    public void update(DormitoryNotice notice, Details details, Instant now) {
        this.notice = notice;
        this.details = details;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    @Embeddable
    public record Details(
            @Column(nullable = false, length = 200) String title,
            @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) Category category,
            @Column(length = 200) String audience,
            @Column(columnDefinition = "text") String description,
            @Column(name = "all_day", nullable = false) boolean allDay,
            @Column(name = "start_at", nullable = false) Instant startAt,
            @Column(name = "end_at") Instant endAt
    ) {}
}
