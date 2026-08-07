package com.dorosoft.erp.audit.application.api.ingest;

import com.dorosoft.erp.audit.domain.record.SensitiveMetadataPolicy;
import com.dorosoft.erp.platform.messaging.audit.AuditActor;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import com.dorosoft.erp.platform.messaging.audit.AuditTarget;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AuditEventContractValidator {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    public void validate(AuditEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");
        require(event.eventId() != null, "eventId is required");
        require("AuditRecorded".equals(event.eventType()), "eventType must be AuditRecorded");
        require(event.eventVersion() == 1, "eventVersion must be 1");
        requireText(event.sourceService(), "sourceService");
        require(event.tenantId() != null, "tenantId is required");
        validateActor(event.actor());
        requireText(event.action(), "action");
        validateTarget(event.target());
        requireText(event.result(), "result");
        require(event.metadata() != null, "metadata is required");
        require(event.traceId() != null && TRACE_ID_PATTERN.matcher(event.traceId()).matches(),
                "traceId has an invalid format");
        require(event.occurredAt() != null, "occurredAt is required");
        SensitiveMetadataPolicy.rejectForbiddenKeys(event.metadata());
    }

    private void validateActor(AuditActor actor) {
        require(actor != null, "actor is required");
        requireText(actor.type(), "actor.type");
        require(actor.id() != null, "actor.id is required");
    }

    private void validateTarget(AuditTarget target) {
        require(target != null, "target is required");
        requireText(target.type(), "target.type");
        require(target.id() != null, "target.id is required");
    }

    private void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
