package com.irummate.domain.notice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "dormitory_notices", indexes = {
        @Index(name = "idx_dormitory_notices_published_id", columnList = "published_at DESC, id DESC")
}, uniqueConstraints = @UniqueConstraint(name = "uk_dormitory_notices_external_id", columnNames = "external_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DormitoryNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "published_at", nullable = false)
    private LocalDate publishedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
