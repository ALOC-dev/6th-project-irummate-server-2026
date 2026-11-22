package com.irummate.domain.privacy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "privacy_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyConsents {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "status", column = @Column(name = "profile_collection_status", nullable = false, length = 20)),
            @AttributeOverride(name = "documentVersion", column = @Column(name = "profile_collection_document_version", length = 20)),
            @AttributeOverride(name = "updatedAt", column = @Column(name = "profile_collection_updated_at"))
    })
    private ConsentChoice profileCollection = ConsentChoice.UNDECIDED;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "status", column = @Column(name = "ai_cert_training_status", nullable = false, length = 20)),
            @AttributeOverride(name = "documentVersion", column = @Column(name = "ai_cert_training_document_version", length = 20)),
            @AttributeOverride(name = "updatedAt", column = @Column(name = "ai_cert_training_updated_at"))
    })
    private ConsentChoice aiCertTraining = ConsentChoice.UNDECIDED;

    public PrivacyConsents(Long userId) {
        this.userId = userId;
    }

    public ConsentChoice choice(ConsentType type) {
        return switch (type) {
            case PROFILE_COLLECTION -> profileCollection;
            case AI_CERT_TRAINING -> aiCertTraining;
        };
    }

    public void change(ConsentType type, ConsentChoice choice) {
        switch (type) {
            case PROFILE_COLLECTION -> profileCollection = choice;
            case AI_CERT_TRAINING -> aiCertTraining = choice;
        }
    }
}
