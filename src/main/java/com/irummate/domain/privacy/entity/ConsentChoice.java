package com.irummate.domain.privacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public record ConsentChoice(
        @Enumerated(EnumType.STRING) @Column(length = 20) Status status,
        @Column(length = 20) String documentVersion,
        Instant updatedAt
) {
    static final ConsentChoice UNDECIDED = new ConsentChoice(Status.NOT_DECIDED, null, null);

    public enum Status { NOT_DECIDED, AGREED, DECLINED, WITHDRAWN }

    public enum Action {
        AGREE(Status.AGREED), DECLINE(Status.DECLINED), WITHDRAW(Status.WITHDRAWN);

        private final Status status;

        public Status status() {
            return status;
        }

        Action(Status status) {
            this.status = status;
        }
    }
}
