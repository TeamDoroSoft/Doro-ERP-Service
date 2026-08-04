package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.port.AuditPayloadSigner;
import com.dorosoft.erp.audit.application.model.AuditRecord;
import com.dorosoft.erp.audit.domain.AuditWriteResult;
import com.dorosoft.erp.audit.domain.AuditWriteStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit writer configured explicitly with the Feature 17 runtime HMAC key ring. */
public final class DefaultAuditWriter implements AuditWriter {
    private final AuditAppendPort appendPort;
    private final AuditPayloadSigner payloadSigner;
    private final AuditRecordValidator validator;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;

    public DefaultAuditWriter(AuditAppendPort appendPort, AuditPayloadSigner payloadSigner,
                              ObjectMapper objectMapper, Clock clock) {
        this.appendPort = Objects.requireNonNull(appendPort);
        this.payloadSigner = Objects.requireNonNull(payloadSigner);
        this.canonicalMapper = objectMapper.rebuild()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        this.validator = new AuditRecordValidator(canonicalMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
        validator.validate(command, context);
        byte[] canonicalPayload = canonicalPayload(command, context);
        byte[] hmac = payloadSigner.sign(canonicalPayload);
        if (hmac == null || hmac.length != 32) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit integrity signer is unavailable");
        }

        Optional<AuditAppendPort.ExistingAuditRecord> existing = findExisting(command);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), canonicalPayload);
        }

        var recordedAt = clock.instant();
        if (recordedAt.isBefore(context.occurredAt())) {
            recordedAt = context.occurredAt();
        }
        var retentionUntil = context.occurredAt().atZone(ZoneOffset.UTC).plusYears(3).toInstant();
        var auditId = UUID.randomUUID();
        AuditRecord record = new AuditRecord(
                auditId,
                command,
                context,
                writeJson(command.beforeValue()),
                writeJson(command.afterValue()),
                hmac,
                retentionUntil,
                recordedAt
        );

        try {
            appendPort.append(record);
            return new AuditWriteResult(AuditWriteStatus.RECORDED, auditId, context.occurredAt());
        } catch (AuditOperationAlreadyExistsException exception) {
            return findExisting(command)
                    .map(found -> replayOrConflict(found, canonicalPayload))
                    .orElseThrow(() -> new AuditContractException(
                            AuditErrorCode.AUDIT_UNAVAILABLE, "Audit duplicate could not be resolved", exception));
        } catch (DataAccessException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit append failed", exception);
        }
    }

    private Optional<AuditAppendPort.ExistingAuditRecord> findExisting(AuditRecordCommand command) {
        try {
            return appendPort.findByOperation(command.domain(), command.operationId(), command.eventSequence());
        } catch (DataAccessException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit append lookup failed", exception);
        }
    }

    private AuditWriteResult replayOrConflict(
            AuditAppendPort.ExistingAuditRecord existing,
            byte[] canonicalPayload
    ) {
        if (!payloadSigner.matches(canonicalPayload, existing.payloadHmac())) {
            throw new AuditContractException(AuditErrorCode.AUDIT_EVENT_CONFLICT, "Audit operation payload conflicts");
        }
        return new AuditWriteResult(AuditWriteStatus.ALREADY_RECORDED, existing.auditId(), existing.occurredAt());
    }

    private byte[] canonicalPayload(AuditRecordCommand command, AuditContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", context.tenantId());
        payload.put("domain", command.domain().name());
        payload.put("action", command.action().name());
        payload.put("operationId", command.operationId().toString());
        payload.put("eventSequence", command.eventSequence());
        payload.put("primaryTarget", command.primaryTarget());
        payload.put("relatedTargets", command.relatedTargets());
        payload.put("beforeValue", command.beforeValue());
        payload.put("afterValue", command.afterValue());
        payload.put("reasonCode", command.reasonCode());
        payload.put("reason", command.reason());
        payload.put("valueSchemaVersion", command.valueSchemaVersion());
        payload.put("actorType", context.actorType().name());
        payload.put("actorId", context.actorId() == null ? null : context.actorId().toString());
        payload.put("actorRoleSnapshot", context.actorRoleSnapshot());
        payload.put("actorDisplayNameSnapshot", context.actorDisplayNameSnapshot());
        payload.put("requestId", context.requestId());
        payload.put("occurredAt", context.occurredAt().toString());
        return writeJson(payload).getBytes(StandardCharsets.UTF_8);
    }

    private String writeJson(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_SCHEMA_UNSUPPORTED, "Audit value is not serializable", exception);
        }
    }

}
