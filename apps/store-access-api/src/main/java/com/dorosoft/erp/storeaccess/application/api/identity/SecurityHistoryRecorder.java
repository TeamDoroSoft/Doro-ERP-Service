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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the account/Kiosk-Credential-change Allowlist events (ADR-02-015). Callers invoke these from
 * within their own {@code @Transactional} business method, right after the triggering save, so the history
 * row commits or rolls back with the business change in the same PostgreSQL transaction — and, for
 * idempotency-wrapped operations, only runs once per Idempotency-Key since it sits inside the guarded
 * operation, not before the claim.
 *
 * <p>{@code recordLoginFailed}/{@code recordAccountLocked} are the exception: ADR-02-015 requires
 * authentication-failure history to commit in its own short Transaction, separate from the caller's account
 * update, so that a history-write problem never turns an already-decided login failure into a {@code 500}.
 * They run with {@link Propagation#REQUIRES_NEW} and callers must catch and swallow (log-only) any exception
 * from them rather than let it propagate.
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailed(UUID tenantId, UUID storeId, UUID employeeId, String reasonCode) {
        recordFailure(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, employeeId, reasonCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccountLocked(UUID tenantId, UUID storeId, UUID employeeId, String reasonCode) {
        recordFailure(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_ACCOUNT_LOCKED, employeeId, reasonCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReauthenticationFailed(UUID tenantId, UUID storeId, UUID employeeId, String reasonCode) {
        recordFailure(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_REAUTHENTICATION_FAILED, employeeId, reasonCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSessionInvalidated(UUID tenantId, UUID storeId, UUID employeeId, String reasonCode) {
        recordFailure(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_SESSION_INVALIDATED, employeeId, reasonCode);
    }

    private void recordFailure(
            UUID tenantId, UUID storeId, SecurityHistoryEventType eventType, UUID employeeId, String reasonCode) {
        EmployeeSecurityHistory history = EmployeeSecurityHistory.occur(
                UUID.randomUUID(), tenantId, storeId, eventType, employeeId, TARGET_TYPE_EMPLOYEE, employeeId,
                SecurityHistoryResult.FAILURE, reasonCode, null, null, clock.instant());
        repository.save(history);
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
