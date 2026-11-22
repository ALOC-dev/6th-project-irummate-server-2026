package com.irummate.domain.privacy.dto;

import com.irummate.domain.privacy.entity.ConsentType;
import java.time.Instant;
import java.util.List;

public record ConsentsResponse(List<ConsentView> consents) {
    public record ConsentView(ConsentType type, boolean required, String status, String documentVersion,
                              String currentDocumentVersion, Instant updatedAt) {
    }
}
