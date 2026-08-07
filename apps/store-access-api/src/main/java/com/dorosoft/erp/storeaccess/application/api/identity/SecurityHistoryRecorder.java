package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Records the account/Kiosk-Credential-change Allowlist events (ADR-02-015). Callers invoke these from
 * within their own {@code @Transactional} business method, right after the triggering save, so the history
 * row commits or rolls back with the business change in the same PostgreSQL transaction — and, for
 * idempotency-wrapped operations, only runs once per Idempotency-Key since it sits inside the guarded
 * operation, not before the claim.
 *
 * <p>Login/lockout/reauthentication/session-invalidation events are intentionally not covered here: those
 * flows don't exist yet (feature 02 steps 2/4 are blocked on feature 01), so their exact Actor/Target shape
 * is still undecided and will be added alongside those flows.
 */
@Component
public class SecurityHistoryRecorder {

    public static final String TARGET_TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String TARGET_TYPE_KIOSK_DEVICE = "KIOSK_DEVICE";

    private final EmployeeSecurityHistoryRepository repository;
    private final Clock clock;

    public SecurityHistoryRecorder(EmployeeSecurityHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void recordEmployeeCreated(UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID newEmployeeId, Role role) {
        record(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_CREATED, actorEmployeeId,
                TARGET_TYPE_EMPLOYEE, newEmployeeId, null, role.name());
    }

    public void recordRoleChanged(
            UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID targetEmployeeId, Role previousRole, Role newRole) {
        record(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_ROLE_CHANGED, actorEmployeeId,
                TARGET_TYPE_EMPLOYEE, targetEmployeeId, previousRole.name(), newRole.name());
    }

    public void recordStatusChanged(
            UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID targetEmployeeId,
            EmployeeStatus previousStatus, EmployeeStatus newStatus) {
        record(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_STATUS_CHANGED, actorEmployeeId,
                TARGET_TYPE_EMPLOYEE, targetEmployeeId, previousStatus.name(), newStatus.name());
    }

    public void recordPasswordChanged(UUID tenantId, UUID storeId, UUID employeeId) {
        record(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_PASSWORD_CHANGED, employeeId,
                TARGET_TYPE_EMPLOYEE, employeeId, null, null);
    }

    public void recordPasswordReset(UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID targetEmployeeId) {
        record(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_PASSWORD_RESET, actorEmployeeId,
                TARGET_TYPE_EMPLOYEE, targetEmployeeId, null, null);
    }

    public void recordKioskDeviceRegistered(UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID kioskDeviceId) {
        record(tenantId, storeId, SecurityHistoryEventType.KIOSK_DEVICE_REGISTERED, actorEmployeeId,
                TARGET_TYPE_KIOSK_DEVICE, kioskDeviceId, null, null);
    }

    public void recordKioskCredentialRotated(
            UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID kioskDeviceId,
            int previousCredentialVersion, int newCredentialVersion) {
        record(tenantId, storeId, SecurityHistoryEventType.KIOSK_CREDENTIAL_ROTATED, actorEmployeeId,
                TARGET_TYPE_KIOSK_DEVICE, kioskDeviceId,
                String.valueOf(previousCredentialVersion), String.valueOf(newCredentialVersion));
    }

    public void recordKioskDeviceRevoked(UUID tenantId, UUID storeId, UUID actorEmployeeId, UUID kioskDeviceId) {
        record(tenantId, storeId, SecurityHistoryEventType.KIOSK_DEVICE_REVOKED, actorEmployeeId,
                TARGET_TYPE_KIOSK_DEVICE, kioskDeviceId, null, null);
    }

    private void record(
            UUID tenantId, UUID storeId, SecurityHistoryEventType eventType, UUID actorEmployeeId,
            String targetType, UUID targetId, String previousValue, String newValue) {
        EmployeeSecurityHistory history = EmployeeSecurityHistory.occur(
                UUID.randomUUID(), tenantId, storeId, eventType, actorEmployeeId, targetType, targetId,
                SecurityHistoryResult.SUCCESS, null, previousValue, newValue, clock.instant());
        repository.save(history);
    }
}
