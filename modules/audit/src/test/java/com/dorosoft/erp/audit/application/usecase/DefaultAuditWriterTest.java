package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.model.AuditRecord;
import com.dorosoft.erp.audit.domain.ActorType;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditPrimaryTarget;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.audit.domain.AuditWriteStatus;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAuditWriterTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-04T10:00:00Z");
    private final InMemoryAppendPort port = new InMemoryAppendPort();
    private final DefaultAuditWriter writer = new DefaultAuditWriter(
            port, this::sha256, new ObjectMapper(), Clock.fixed(OCCURRED_AT.plusSeconds(1), ZoneOffset.UTC));

    @Test
    void recordsThenReplaysTheSameCanonicalPayload() {
        AuditRecordCommand command = command("ACTIVE");

        var first = writer.record(command, context());
        var second = writer.record(command, context());

        assertEquals(AuditWriteStatus.RECORDED, first.status());
        assertEquals(AuditWriteStatus.ALREADY_RECORDED, second.status());
        assertEquals(first.auditId(), second.auditId());
        assertEquals(OCCURRED_AT.atZone(ZoneOffset.UTC).plusYears(3).toInstant(), port.record.retentionUntil());
    }

    @Test
    void rejectsTheSameOperationKeyWithDifferentPayload() {
        AuditRecordCommand first = command("ACTIVE");
        writer.record(first, context());
        AuditRecordCommand conflicting = new AuditRecordCommand(
                first.domain(), first.action(), first.operationId(), first.eventSequence(), first.primaryTarget(),
                first.relatedTargets(), first.beforeValue(),
                Map.of("status", "INACTIVE", "role", "ADMIN", "permissionCodes", List.of("account.read")),
                first.reasonCode(), first.reason(), first.valueSchemaVersion());

        AuditContractException exception = assertThrows(AuditContractException.class,
                () -> writer.record(conflicting, context()));
        assertEquals(AuditErrorCode.AUDIT_EVENT_CONFLICT, exception.code());
    }

    private AuditRecordCommand command(String status) {
        return new AuditRecordCommand(
                AuditDomain.IDENTITY, AuditAction.EMPLOYEE_ACCOUNT_CREATED, UUID.randomUUID(), 0,
                new AuditPrimaryTarget(AuditTargetType.ACCOUNT, UUID.randomUUID()), List.of(), Map.of(),
                Map.of("status", status, "role", "ADMIN", "permissionCodes", List.of("account.read")),
                null, null, 1);
    }

    private AuditContext context() {
        return new AuditContext("local-store", ActorType.ADMIN, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "ADMIN", "관리자", "req-1", OCCURRED_AT);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class InMemoryAppendPort implements AuditAppendPort {
        private AuditRecord record;

        @Override
        public void append(AuditRecord record) {
            this.record = record;
        }

        @Override
        public Optional<ExistingAuditRecord> findByOperation(AuditDomain domain, UUID operationId, int eventSequence) {
            if (record == null || record.command().domain() != domain
                    || !record.command().operationId().equals(operationId)
                    || record.command().eventSequence() != eventSequence) {
                return Optional.empty();
            }
            return Optional.of(new ExistingAuditRecord(record.auditId(),
                    Arrays.copyOf(record.payloadHmac(), record.payloadHmac().length), record.context().occurredAt()));
        }
    }
}
