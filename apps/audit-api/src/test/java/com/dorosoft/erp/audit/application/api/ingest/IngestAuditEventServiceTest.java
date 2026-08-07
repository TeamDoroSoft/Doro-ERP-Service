package com.dorosoft.erp.audit.application.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.audit.application.port.AuditRecordRepositoryPort;
import com.dorosoft.erp.audit.application.port.SaveOutcome;
import com.dorosoft.erp.audit.domain.record.AuditRecord;
import com.dorosoft.erp.platform.messaging.audit.AuditActor;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import com.dorosoft.erp.platform.messaging.audit.AuditTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestAuditEventServiceTest {

    @Test
    void savedEventUsesReceivedClockButExpiresFromOccurredAt() {
        CapturingRepository repository = new CapturingRepository(SaveOutcome.SAVED);
        IngestAuditEventService service = service(repository, "2026-08-07T10:00:00Z");

        IngestOutcome outcome = service.handle(event());

        assertThat(outcome).isEqualTo(IngestOutcome.SAVED);
        assertThat(repository.saved.receivedAt()).isEqualTo(Instant.parse("2026-08-07T10:00:00Z"));
        assertThat(repository.saved.expiresAt()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void delayedReceiptDoesNotChangeExpiryForTheSameOccurredAt() {
        CapturingRepository firstRepository = new CapturingRepository(SaveOutcome.SAVED);
        CapturingRepository delayedRepository = new CapturingRepository(SaveOutcome.SAVED);

        service(firstRepository, "2026-01-02T00:00:00Z").handle(event());
        service(delayedRepository, "2026-03-01T00:00:00Z").handle(event());

        assertThat(firstRepository.saved.receivedAt()).isNotEqualTo(delayedRepository.saved.receivedAt());
        assertThat(firstRepository.saved.expiresAt()).isEqualTo(delayedRepository.saved.expiresAt());
    }

    @Test
    void duplicateRepositoryOutcomeIsReportedAsAlreadyExisting() {
        CapturingRepository repository = new CapturingRepository(SaveOutcome.DUPLICATE);

        IngestOutcome outcome = service(repository, "2026-08-07T10:00:00Z").handle(event());

        assertThat(outcome).isEqualTo(IngestOutcome.ALREADY_EXISTS);
    }

    private IngestAuditEventService service(CapturingRepository repository, String receivedAt) {
        return new IngestAuditEventService(
                repository,
                new AuditEventContractValidator(),
                Clock.fixed(Instant.parse(receivedAt), ZoneOffset.UTC),
                Duration.ofDays(90));
    }

    private AuditEventEnvelope event() {
        return new AuditEventEnvelope(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "AuditRecorded",
                1,
                "commerce",
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                null,
                new AuditActor(
                        "EMPLOYEE",
                        UUID.fromString("40000000-0000-0000-0000-000000000004"),
                        "MANAGER"),
                "ORDER_ACCEPTED",
                new AuditTarget(
                        "ORDER",
                        UUID.fromString("50000000-0000-0000-0000-000000000005")),
                "SUCCESS",
                null,
                Map.of("orderNumber", "A-001"),
                "req-123",
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class CapturingRepository implements AuditRecordRepositoryPort {

        private final SaveOutcome outcome;
        private AuditRecord saved;

        private CapturingRepository(SaveOutcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public SaveOutcome save(AuditRecord record) {
            saved = record;
            return outcome;
        }
    }
}
