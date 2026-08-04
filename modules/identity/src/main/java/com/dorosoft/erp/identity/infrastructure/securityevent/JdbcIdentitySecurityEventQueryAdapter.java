package com.dorosoft.erp.identity.infrastructure.securityevent;

import com.dorosoft.erp.identity.application.history.IdentityAuditSortKey;
import com.dorosoft.erp.identity.application.history.IdentityAuditSourceType;
import com.dorosoft.erp.identity.application.history.IdentitySecurityEventQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public final class JdbcIdentitySecurityEventQueryAdapter implements IdentitySecurityEventQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcIdentitySecurityEventQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SecurityEventView> query(
            String eventType,
            UUID targetAccountId,
            Instant from,
            Instant to,
            IdentityAuditSortKey cursorBoundary,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT security_event_id, event_type, outcome, account_id,
                       failure_class, occurred_at, request_id
                  FROM identity_security_event
                 WHERE occurred_at >= ? AND occurred_at < ?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(Timestamp.from(from));
        arguments.add(Timestamp.from(to));
        if (eventType != null) {
            sql.append(" AND event_type = ? ");
            arguments.add(eventType);
        }
        if (targetAccountId != null) {
            sql.append(" AND account_id = ? ");
            arguments.add(targetAccountId.toString());
        }
        if (cursorBoundary != null) {
            if (cursorBoundary.sourceType() == IdentityAuditSourceType.IDENTITY_SECURITY_EVENT) {
                sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND security_event_id < ?)) ");
                arguments.add(Timestamp.from(cursorBoundary.occurredAt()));
                arguments.add(Timestamp.from(cursorBoundary.occurredAt()));
                arguments.add(cursorBoundary.eventId().toString());
            } else {
                sql.append(" AND occurred_at < ? ");
                arguments.add(Timestamp.from(cursorBoundary.occurredAt()));
            }
        }
        sql.append(" ORDER BY occurred_at DESC, security_event_id DESC LIMIT ? ");
        arguments.add(limit);
        return jdbc.query(sql.toString(), (resultSet, rowNumber) -> new SecurityEventView(
                UUID.fromString(resultSet.getString("security_event_id")),
                resultSet.getString("event_type"),
                resultSet.getString("outcome"),
                uuid(resultSet.getString("account_id")),
                resultSet.getString("failure_class"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getString("request_id")
        ), arguments.toArray());
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
