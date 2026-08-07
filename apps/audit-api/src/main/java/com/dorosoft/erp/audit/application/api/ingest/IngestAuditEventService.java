package com.dorosoft.erp.audit.application.api.ingest;

import com.dorosoft.erp.audit.application.port.AuditRecordRepositoryPort;
import com.dorosoft.erp.audit.application.port.SaveOutcome;
import com.dorosoft.erp.audit.domain.record.AuditRecord;
import com.dorosoft.erp.audit.domain.record.AuditSource;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class IngestAuditEventService implements IngestAuditEventUseCase {

    private final AuditRecordRepositoryPort repository;
    private final AuditEventContractValidator validator;
    private final Clock clock;
    private final Duration retention;

    public IngestAuditEventService(
            AuditRecordRepositoryPort repository,
            AuditEventContractValidator validator,
            Clock clock,
            Duration retention) {
        this.repository = Objects.requireNonNull(repository);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
        this.retention = Objects.requireNonNull(retention);
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention must be positive");
        }
    }

    @Override
    public IngestOutcome handle(AuditEventEnvelope event) {
        validator.validate(event);
        Instant receivedAt = clock.instant();
        AuditRecord record = new AuditRecord(
                new AuditSource(event.sourceService(), event.eventId()),
                event.eventVersion(),
                event.tenantId(),
                event.storeId(),
                new com.dorosoft.erp.audit.domain.record.AuditActor(
                        event.actor().type(), event.actor().id(), event.actor().role()),
                event.action(),
                new com.dorosoft.erp.audit.domain.record.AuditTarget(
                        event.target().type(), event.target().id()),
                event.result(),
                event.reasonCode(),
                event.metadata(),
                event.traceId(),
                event.occurredAt(),
                receivedAt,
                event.occurredAt().plus(retention));

        return repository.save(record) == SaveOutcome.SAVED
                ? IngestOutcome.SAVED
                : IngestOutcome.ALREADY_EXISTS;
    }
}
