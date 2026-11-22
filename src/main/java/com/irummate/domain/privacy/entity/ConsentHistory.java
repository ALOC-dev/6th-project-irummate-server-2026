package com.irummate.domain.privacy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "privacy_consent_history", indexes = @Index(
        name = "idx_privacy_consent_history_latest", columnList = "user_id, consent_type, id DESC"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, updatable = false, length = 50)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ConsentChoice.Action action;

    @Column(name = "document_version", nullable = false, updatable = false, length = 20)
    private String documentVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConsentHistory(Long userId, ConsentType type, ConsentChoice.Action action, String version, Instant at) {
        this.userId = userId;
        this.consentType = type;
        this.action = action;
        this.documentVersion = version;
        this.createdAt = at;
    }

    public boolean matches(ConsentChoice.Action expectedAction, String version) {
        return action == expectedAction && documentVersion.equals(version);
    }
}
