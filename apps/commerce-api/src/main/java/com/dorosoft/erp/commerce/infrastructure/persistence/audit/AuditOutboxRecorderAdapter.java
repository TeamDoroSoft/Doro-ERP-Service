package com.dorosoft.erp.commerce.infrastructure.persistence.audit;

import com.dorosoft.erp.commerce.application.api.audit.AuditRecord;
import com.dorosoft.erp.commerce.application.port.audit.AuditRecorderPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Audit Event를 업무 변경과 같은 Local Transaction의 Outbox에 기록한다.
 *
 * <p>중앙 Audit Service를 동기 호출하지 않는다. Outbox Publisher는 별도 Slice가 소유한다.
 */
@Component
class AuditOutboxRecorderAdapter implements AuditRecorderPort {

    static final String DESTINATION_AUDIT = "audit-events.fifo";
    static final String SOURCE_SERVICE = "commerce";
    static final int EVENT_VERSION = 1;
    private static final String STATUS_PENDING = "PENDING";

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AuditOutboxRecorderAdapter(EntityManager entityManager, ObjectMapper objectMapper, Clock clock) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(AuditRecord auditRecord) {
        Instant occurredAt = clock.instant();
        UUID eventId = UUID.randomUUID();

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", auditRecord.actor().actorType().name());
        actor.put("id", auditRecord.actor().actorId().toString());
        actor.put("role", auditRecord.actor().role().name());

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("type", auditRecord.targetType());
        target.put("id", auditRecord.targetId().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", auditRecord.action().name());
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("sourceService", SOURCE_SERVICE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("tenantId", auditRecord.actor().tenantId().toString());
        if (auditRecord.actor().storeId() != null) {
            envelope.put("storeId", auditRecord.actor().storeId().toString());
        }
        envelope.put("actor", actor);
        envelope.put("action", auditRecord.action().name());
        envelope.put("target", target);
        envelope.put("result", "SUCCESS");
        envelope.put("metadata", auditRecord.metadata());
        String traceId = MDC.get("requestId");
        if (traceId != null && !traceId.isBlank()) {
            envelope.put("traceId", traceId);
        }

        OutboxEventEntity entity = new OutboxEventEntity(
                UUID.randomUUID(),
                eventId,
                DESTINATION_AUDIT,
                auditRecord.action().name(),
                EVENT_VERSION,
                auditRecord.targetType(),
                auditRecord.targetId(),
                auditRecord.actor().tenantId(),
                auditRecord.actor().storeId(),
                auditRecord.actor().tenantId().toString(),
                serialize(envelope),
                STATUS_PENDING,
                occurredAt);
        entityManager.persist(entity);
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("audit event payload could not be serialized", exception);
        }
    }
}
