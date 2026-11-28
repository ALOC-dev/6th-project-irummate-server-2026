package com.irummate.domain.calendar.entity;

import com.irummate.domain.notice.entity.DormitoryNotice;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_extraction_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_calendar_extractions_notice_version",
                columnNames = {"notice_id", "body_version"}),
        indexes = @Index(name = "idx_calendar_extractions_status_next",
                columnList = "status,next_attempt_at,id"))
public class CalendarExtractionJob {
    public enum Status { PENDING, PROCESSING, COMPLETED, REVIEW_REQUIRED, REVIEWED, FAILED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private DormitoryNotice notice;

    @Column(name = "body_version", nullable = false)
    private int bodyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(columnDefinition = "jsonb")
    private String result;

    @Column(name = "last_error", length = 80)
    private String lastError;

    protected CalendarExtractionJob() {}
}
