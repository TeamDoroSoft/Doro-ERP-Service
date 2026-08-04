package com.dorosoft.erp.audit.infrastructure;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;
import com.dorosoft.erp.audit.application.model.AuditRecord;
import com.dorosoft.erp.audit.application.model.PrivacyAccessRecord;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.port.AuditReadPort;
import com.dorosoft.erp.audit.application.port.PrivacyAccessAppendPort;
import com.dorosoft.erp.audit.application.usecase.AuditOperationAlreadyExistsException;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditRelationType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public final class JdbcAuditRepository implements AuditAppendPort, AuditReadPort, PrivacyAccessAppendPort {
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditRecord record) {
        try {
            jdbc.update("""
                            INSERT INTO audit_record (
                                audit_id, domain, action, operation_id, event_sequence,
                                actor_type, actor_id, actor_role_snapshot, actor_display_name_snapshot,
                                target_type, target_id, before_value, after_value, reason_code, reason,
                                value_schema_version, retention_class, retention_until, payload_hmac,
                                request_id, occurred_at, recorded_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    record.auditId().toString(), record.command().domain().name(), record.command().action().name(),
                    record.command().operationId().toString(), record.command().eventSequence(),
                    record.context().actorType().name(), uuid(record.context().actorId()),
                    record.context().actorRoleSnapshot(), record.context().actorDisplayNameSnapshot(),
                    record.command().primaryTarget().targetType().name(), record.command().primaryTarget().targetId().toString(),
                    record.beforeJson(), record.afterJson(), record.command().reasonCode(), record.command().reason(),
                    record.command().valueSchemaVersion(), "BUSINESS_AUDIT_3Y", timestamp(record.retentionUntil()),
                    record.payloadHmac(), record.context().requestId(), timestamp(record.context().occurredAt()),
                    timestamp(record.recordedAt()));

            appendTarget(record, AuditRelationType.PRIMARY.name(),
                    record.command().primaryTarget().targetType().name(), record.command().primaryTarget().targetId());
            record.command().relatedTargets().forEach(target -> appendTarget(
                    record, target.relationType().name(), target.targetType().name(), target.targetId()));
        } catch (DuplicateKeyException exception) {
            throw new AuditOperationAlreadyExistsException(exception);
        }
    }

    private void appendTarget(AuditRecord record, String relation, String targetType, UUID targetId) {
        jdbc.update("""
                        INSERT INTO audit_record_target (audit_id, relation_type, target_type, target_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                record.auditId().toString(), relation, targetType, targetId.toString(),
                timestamp(record.context().occurredAt()));
    }

    @Override
    public Optional<ExistingAuditRecord> findByOperation(AuditDomain domain, UUID operationId, int eventSequence) {
        List<ExistingAuditRecord> records = jdbc.query("""
                        SELECT audit_id, payload_hmac, occurred_at
                          FROM audit_record
                         WHERE domain = ? AND operation_id = ? AND event_sequence = ?
                        """,
                (resultSet, rowNumber) -> new ExistingAuditRecord(
                        UUID.fromString(resultSet.getString("audit_id")),
                        resultSet.getBytes("payload_hmac"),
                        resultSet.getTimestamp("occurred_at").toInstant()),
                domain.name(), operationId.toString(), eventSequence);
        return records.stream().findFirst();
    }

    @Override
    public void append(PrivacyAccessRecord record) {
        byte[] encryptedAddress = decodeCiphertext(record.context().clientAddressCiphertext());
        jdbc.update("""
                        INSERT INTO privacy_access_log (
                            access_log_id, accessor_type, accessor_id, accessor_role_snapshot,
                            purpose_code, access_action, resource_type, result_code, result_count,
                            client_address_ciphertext, client_address_key_version, request_id,
                            accessed_at, retention_until
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.accessLogId().toString(), record.context().accessorType(), uuid(record.context().accessorId()),
                record.context().accessorRoleSnapshot(), record.command().purposeCode().name(),
                record.command().accessAction().name(), record.command().resourceType().name(),
                record.command().resultCode().name(), record.command().resultCount(), encryptedAddress,
                record.context().clientAddressKeyVersion(), record.context().requestId(),
                timestamp(record.context().accessedAt()), timestamp(record.retentionUntil()));
        record.command().subjects().forEach(subject -> jdbc.update("""
                        INSERT INTO privacy_access_log_subject (access_log_id, subject_type, subject_id)
                        VALUES (?, ?, ?)
                        """,
                record.accessLogId().toString(), subject.subjectType(), subject.subjectId().toString()));
    }

    @Override
    public List<AuditRecordProjection> query(AuditQueryFilter filter, Instant beforeOccurredAt,
                                             UUID beforeAuditId, int limit, Instant now) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT ar.* FROM audit_record ar
                """);
        List<Object> arguments = new ArrayList<>();
        if (filter.targetType() != null || filter.targetId() != null) {
            sql.append(" JOIN audit_record_target art ON art.audit_id = ar.audit_id ");
        }
        sql.append(" WHERE ar.retention_until > ? ");
        arguments.add(Timestamp.from(now));
        if (filter.domain() != null) {
            sql.append(" AND ar.domain = ? ");
            arguments.add(filter.domain().name());
        }
        if (filter.action() != null) {
            sql.append(" AND ar.action = ? ");
            arguments.add(filter.action());
        }
        if (filter.actorId() != null) {
            sql.append(" AND ar.actor_id = ? ");
            arguments.add(filter.actorId().toString());
        }
        if (filter.targetType() != null) {
            sql.append(" AND art.target_type = ? ");
            arguments.add(filter.targetType().name());
        }
        if (filter.targetId() != null) {
            sql.append(" AND art.target_id = ? ");
            arguments.add(filter.targetId().toString());
        }
        sql.append(" AND ar.occurred_at >= ? AND ar.occurred_at < ? ");
        arguments.add(timestamp(filter.from()));
        arguments.add(timestamp(filter.to()));
        if (beforeOccurredAt != null) {
            sql.append(" AND (ar.occurred_at < ? OR (ar.occurred_at = ? AND ar.audit_id < ?)) ");
            arguments.add(timestamp(beforeOccurredAt));
            arguments.add(timestamp(beforeOccurredAt));
            arguments.add(beforeAuditId.toString());
        }
        sql.append(" ORDER BY ar.occurred_at DESC, ar.audit_id DESC LIMIT ? ");
        arguments.add(limit);
        return jdbc.query(sql.toString(), (resultSet, rowNumber) -> projection(resultSet), arguments.toArray());
    }

    @Override
    public Optional<AuditRecordProjection> findById(UUID auditId, Instant now) {
        List<AuditRecordProjection> records = jdbc.query("""
                        SELECT * FROM audit_record WHERE audit_id = ? AND retention_until > ?
                        """, (resultSet, rowNumber) -> projection(resultSet), auditId.toString(), timestamp(now));
        return records.stream().findFirst();
    }

    private AuditRecordProjection projection(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> before = readJson(resultSet.getString("before_value"));
        Map<String, Object> after = readJson(resultSet.getString("after_value"));
        List<String> changed = new ArrayList<>();
        var fields = new java.util.TreeSet<String>();
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());
        fields.stream().filter(field -> !java.util.Objects.equals(before.get(field), after.get(field))).forEach(changed::add);
        return new AuditRecordProjection(
                UUID.fromString(resultSet.getString("audit_id")), resultSet.getString("domain"),
                resultSet.getString("action"), resultSet.getString("actor_type"), uuid(resultSet.getString("actor_id")),
                resultSet.getString("actor_role_snapshot"), resultSet.getString("actor_display_name_snapshot"),
                resultSet.getString("reason_code"), resultSet.getString("reason"),
                com.dorosoft.erp.audit.domain.AuditTargetType.valueOf(resultSet.getString("target_type")),
                UUID.fromString(resultSet.getString("target_id")), changed,
                resultSet.getTimestamp("occurred_at").toInstant(), resultSet.getString("request_id"),
                before, after, resultSet.getInt("value_schema_version"));
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (JacksonException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Stored audit value is invalid", exception);
        }
    }

    private byte[] decodeCiphertext(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(ciphertext.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw new AuditContractException(
                    AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, "Encrypted client address is invalid", exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
