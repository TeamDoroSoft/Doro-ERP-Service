package com.dorosoft.erp.storeaccess.application.port.identity;

import java.util.UUID;

/**
 * Deletes every Redis Session registered for an employee, after the caller's own PostgreSQL Transaction has
 * committed (ADR-02-002/004): Role/status/password changes must not leave a stale Session usable with
 * superseded permissions or credentials. Never rolls the caller's completed business change back on failure —
 * callers must not wrap this in the same {@code @Transactional} boundary as the account write.
 */
public interface EmployeeSessionRevoker {

    void revokeAllSessions(UUID tenantId, UUID employeeId);
}
