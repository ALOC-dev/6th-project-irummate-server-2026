package com.irummate.domain.privacy.dto;

import com.irummate.domain.privacy.entity.ConsentChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public final class ConsentRequests {
    private ConsentRequests() {
    }

    public record OnboardingRequest(@Valid @NotNull Agreement profileCollection,
                                    @Valid @NotNull Agreement aiCertTraining) {
    }

    public record Agreement(@NotNull Boolean agreed, String documentVersion) {
    }

    public record ChangeRequest(@NotNull ConsentChoice.Action action, String documentVersion) {
    }
}
