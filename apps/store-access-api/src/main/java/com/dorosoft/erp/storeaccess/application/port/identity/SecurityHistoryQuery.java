package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Repository-facing local security history search criteria (ADR-02-015). {@code restrictToActorId}, when
 * present, limits results to records where the given employee is the <em>target</em> (their own account
 * history), the target is a {@code STAFF} employee, or the target is a Kiosk device — the MANAGER scope.
 * Being the record's {@code actorEmployeeId} does not by itself grant visibility: a MANAGER who acted on an
 * OWNER or another MANAGER must not see that record. {@code null} means no restriction (OWNER).
 * {@code cursorOccurredAt}/{@code cursorId} are both present or both absent, matching the
 * {@code occurredAt + id} cursor.
 */
public record SecurityHistoryQuery(
        UUID tenantId,
        Instant from,
        Instant to,
        SecurityHistoryEventType eventType,
        String targetType,
        UUID targetId,
        SecurityHistoryResult result,
        Instant cursorOccurredAt,
        UUID cursorId,
        int size,
        UUID restrictToActorId) {
}
