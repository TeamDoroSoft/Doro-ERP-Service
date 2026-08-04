package com.dorosoft.erp.identity.domain.securityevent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 수정·삭제하지 않는 Identity 보안 Event 한 건. */
public record IdentitySecurityEvent(
        UUID securityEventId,
        UUID accountId,
        SecurityEventType eventType,
        SecurityEventOutcome outcome,
        SecurityEventFailureClass failureClass,
        String requestId,
        Instant occurredAt
) {
    public IdentitySecurityEvent {
        Objects.requireNonNull(securityEventId, "securityEventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (requestId.isBlank() || requestId.length() > 100) {
            throw new IllegalArgumentException("requestId must contain 1 to 100 characters");
        }
        if (!SecurityEventPolicy.isValidCombination(
                eventType, outcome, failureClass, accountId != null
        )) {
            throw new IllegalArgumentException("unsupported identity security event combination");
        }
    }
}
