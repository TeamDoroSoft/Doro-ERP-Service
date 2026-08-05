package com.dorosoft.erp.audit.application.usecase;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditQueryResult;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;
import com.dorosoft.erp.audit.application.port.AuditCursorCodec;
import com.dorosoft.erp.audit.application.port.AuditReadPort;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditActionSchemaRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Query implementation configured explicitly with the Feature 17 signed-cursor key ring. */
public class DefaultAuditQuery implements AuditQuery {
    private static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
    private static final Duration MAX_PERIOD = Duration.ofDays(366);

    private final AuditReadPort readPort;
    private final AuditCursorCodec cursorCodec;
    private final Clock clock;

    public DefaultAuditQuery(AuditReadPort readPort, AuditCursorCodec cursorCodec, Clock clock) {
        this.readPort = Objects.requireNonNull(readPort);
        this.cursorCodec = Objects.requireNonNull(cursorCodec);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditQueryResult query(String tenantId, AuditQueryFilter requested) {
        if (tenantId == null || tenantId.isBlank() || requested == null) {
            throw error(AuditErrorCode.INVALID_AUDIT_FILTER, "Audit query context is invalid");
        }
        int limit = requested.limit() == 0 ? 20 : requested.limit();
        if (limit < 1 || limit > 100) {
            throw error(AuditErrorCode.INVALID_LIMIT, "Audit query limit is invalid");
        }
        Instant to = requested.to() == null ? clock.instant() : requested.to();
        Instant from = requested.from() == null ? to.minus(DEFAULT_PERIOD) : requested.from();
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
            throw error(AuditErrorCode.INVALID_PERIOD, "Audit query period is invalid");
        }
        if (requested.action() != null) {
            AuditAction action;
            try {
                action = AuditAction.valueOf(requested.action());
            } catch (IllegalArgumentException exception) {
                throw error(AuditErrorCode.INVALID_AUDIT_FILTER, "Audit action filter is invalid");
            }
            if (requested.domain() != null && AuditActionSchemaRegistry.domainFor(action) != requested.domain()) {
                throw error(AuditErrorCode.INVALID_AUDIT_FILTER, "Audit domain and action filter do not match");
            }
        }
        AuditQueryFilter filter = new AuditQueryFilter(
                requested.domain(), requested.action(), requested.actorId(), requested.targetType(), requested.targetId(),
                from, to, limit, requested.cursor());
        AuditCursorCodec.Position position = requested.cursor() == null
                ? null : cursorCodec.decode(tenantId, filter, requested.cursor());
        try {
            var records = readPort.query(filter,
                    position == null ? null : position.occurredAt(),
                    position == null ? null : position.auditId(), limit + 1, clock.instant());
            boolean hasNext = records.size() > limit;
            var items = hasNext ? records.subList(0, limit) : records;
            String next = null;
            if (hasNext && !items.isEmpty()) {
                AuditRecordProjection last = items.getLast();
                next = cursorCodec.encode(tenantId, filter, last.occurredAt(), last.auditId());
            }
            return new AuditQueryResult(items, next);
        } catch (DataAccessException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit query failed", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditRecordProjection> findById(String tenantId, UUID auditId) {
        if (tenantId == null || tenantId.isBlank() || auditId == null) {
            throw error(AuditErrorCode.INVALID_AUDIT_FILTER, "Audit detail context is invalid");
        }
        try {
            return readPort.findById(auditId, clock.instant());
        } catch (DataAccessException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit detail query failed", exception);
        }
    }

    private AuditContractException error(AuditErrorCode code, String message) {
        return new AuditContractException(code, message);
    }
}
