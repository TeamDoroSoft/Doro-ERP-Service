package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionRevoker;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Employee creation, Role/status management and password administration, including the OWNER/MANAGER
 * authorization matrix, the self-account-change block, and last-active-OWNER protection (ADR-02-010).
 * {@link ActorContext} stands in for the authenticated Session until feature 02 step 2's login is complete;
 * a future Controller will build it from the Session instead of a caller-supplied value.
 */
@Service
public class EmployeeManagementService {

    private static final String CREATE_EMPLOYEE_OPERATION = "CREATE_EMPLOYEE";
    private static final String RESET_PASSWORD_OPERATION = "RESET_PASSWORD";

    private final EmployeeAccountRepository employeeAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final IdempotencyService idempotencyService;
    private final SecurityHistoryRecorder securityHistoryRecorder;
    private final EmployeeSessionRevoker employeeSessionRevoker;
    private final Clock clock;

    public EmployeeManagementService(
            EmployeeAccountRepository employeeAccountRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            IdempotencyService idempotencyService,
            SecurityHistoryRecorder securityHistoryRecorder,
            EmployeeSessionRevoker employeeSessionRevoker,
            Clock clock) {
        this.employeeAccountRepository = employeeAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.idempotencyService = idempotencyService;
        this.securityHistoryRecorder = securityHistoryRecorder;
        this.employeeSessionRevoker = employeeSessionRevoker;
        this.clock = clock;
    }

    @Transactional
    public EmployeeAccount createEmployee(
            ActorContext actor, LoginId loginId, String temporaryPasswordHash, Role targetRole) {
        requireCanManage(actor, targetRole);
        Instant now = clock.instant();
        EmployeeAccount created = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), actor.tenantId(), actor.storeId(), loginId, temporaryPasswordHash, targetRole, now);
        EmployeeAccount saved = employeeAccountRepository.save(created);
        securityHistoryRecorder.recordEmployeeCreated(
                actor.tenantId(), actor.storeId(), actor.employeeId(), saved.id(), targetRole);
        return saved;
    }

    /** Idempotent employee creation (ADR-02-014): validates and authorizes before claiming the key. */
    @Transactional
    public EmployeeIdentitySnapshot createEmployeeIdempotently(
            ActorContext actor, String idempotencyKey, LoginId loginId, String temporaryPasswordRaw, Role targetRole) {
        requireCanManage(actor, targetRole);
        String temporaryPassword = PasswordPolicyValidator.normalize(temporaryPasswordRaw);
        passwordPolicyValidator.validate(temporaryPassword, loginId, null);
        String canonicalRequest = canonicalRequest(
                actor, CREATE_EMPLOYEE_OPERATION, loginId.value(), targetRole.name(), temporaryPassword);

        return idempotencyService.execute(
                actor.tenantId(), actor.employeeId(), CREATE_EMPLOYEE_OPERATION, idempotencyKey, canonicalRequest,
                EmployeeIdentitySnapshot::serialize, EmployeeIdentitySnapshot::deserialize,
                () -> {
                    String hash = passwordEncoder.encode(temporaryPassword);
                    EmployeeAccount created = EmployeeAccount.createWithTemporaryPassword(
                            UUID.randomUUID(), actor.tenantId(), actor.storeId(), loginId, hash, targetRole,
                            clock.instant());
                    EmployeeAccount saved = employeeAccountRepository.save(created);
                    securityHistoryRecorder.recordEmployeeCreated(
                            actor.tenantId(), actor.storeId(), actor.employeeId(), saved.id(), targetRole);
                    return EmployeeIdentitySnapshot.from(saved);
                });
    }

    /** Controller-facing overload: {@code newRoleRaw} must already be validated against the Role enum names
     * (e.g. via a {@code @Pattern}-constrained Request DTO) since Controllers may not reference {@link Role}. */
    @Transactional
    public EmployeeSummary createEmployeeIdempotently(
            ActorContext actor, String idempotencyKey, String loginIdRaw, String temporaryPasswordRaw, String targetRoleRaw) {
        LoginId loginId = parseLoginId(loginIdRaw);
        EmployeeIdentitySnapshot snapshot = createEmployeeIdempotently(
                actor, idempotencyKey, loginId, temporaryPasswordRaw, Role.valueOf(targetRoleRaw));
        EmployeeAccount created = employeeAccountRepository.findById(snapshot.employeeId())
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));
        return EmployeeSummary.from(created);
    }

    public List<EmployeeSummary> listEmployees(ActorContext actor) {
        requireCanView(actor);
        return employeeAccountRepository.findByTenantId(actor.tenantId()).stream()
                .filter(account -> actor.role() == Role.OWNER
                        || account.role() == Role.STAFF
                        || account.id().equals(actor.employeeId()))
                .map(EmployeeSummary::from)
                .toList();
    }

    public EmployeeSummary getEmployee(ActorContext actor, UUID targetEmployeeId) {
        requireCanView(actor);
        EmployeeAccount target = employeeAccountRepository.findById(targetEmployeeId)
                .filter(candidate -> candidate.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));
        if (!target.id().equals(actor.employeeId())) {
            requireCanManage(actor, target.role());
        }
        return EmployeeSummary.from(target);
    }

    /** Controller-facing overload: {@code newRoleRaw} must already be validated against the Role enum names. */
    @Transactional
    public EmployeeSummary changeRole(ActorContext actor, UUID targetEmployeeId, String newRoleRaw) {
        return EmployeeSummary.from(changeRole(actor, targetEmployeeId, Role.valueOf(newRoleRaw)));
    }

    @Transactional
    public EmployeeAccount changeRole(ActorContext actor, UUID targetEmployeeId, Role newRole) {
        EmployeeAccount target = requireManageableTarget(actor, targetEmployeeId);
        requireCanManage(actor, target.role());
        requireCanManage(actor, newRole);
        if (target.role() == Role.OWNER && newRole != Role.OWNER) {
            requireNotLastActiveOwner(actor.tenantId(), target);
        }
        EmployeeAccount saved = employeeAccountRepository.save(target.changeRole(newRole, clock.instant()));
        securityHistoryRecorder.recordRoleChanged(
                actor.tenantId(), actor.storeId(), actor.employeeId(), target.id(), target.role(), newRole);
        revokeSessionsAfterCommit(actor.tenantId(), target.id());
        return saved;
    }

    /** Controller-facing overload: {@code newStatusRaw} must already be validated against the EmployeeStatus
     * enum names. */
    @Transactional
    public EmployeeSummary changeStatus(ActorContext actor, UUID targetEmployeeId, String newStatusRaw) {
        return EmployeeSummary.from(changeStatus(actor, targetEmployeeId, EmployeeStatus.valueOf(newStatusRaw)));
    }

    @Transactional
    public EmployeeAccount changeStatus(ActorContext actor, UUID targetEmployeeId, EmployeeStatus newStatus) {
        EmployeeAccount target = requireManageableTarget(actor, targetEmployeeId);
        requireCanManage(actor, target.role());
        if (target.role() == Role.OWNER
                && target.status() == EmployeeStatus.ACTIVE
                && newStatus == EmployeeStatus.INACTIVE) {
            requireNotLastActiveOwner(actor.tenantId(), target);
        }
        EmployeeAccount saved = employeeAccountRepository.save(target.changeStatus(newStatus, clock.instant()));
        securityHistoryRecorder.recordStatusChanged(
                actor.tenantId(), actor.storeId(), actor.employeeId(), target.id(), target.status(), newStatus);
        revokeSessionsAfterCommit(actor.tenantId(), target.id());
        return saved;
    }

    /**
     * Idempotent administrator password reset (ADR-02-009/014). {@code requireManageableTarget} both hides
     * cross-tenant targets as {@code 404} and blocks resetting one's own password through this admin API —
     * only {@link #changeOwnPassword} may do that.
     */
    @Transactional
    public EmployeeIdentitySnapshot resetPassword(
            ActorContext actor, String idempotencyKey, UUID targetEmployeeId, String newTemporaryPasswordRaw) {
        EmployeeAccount target = requireManageableTarget(actor, targetEmployeeId);
        requireCanManage(actor, target.role());
        String newTemporaryPassword = PasswordPolicyValidator.normalize(newTemporaryPasswordRaw);
        passwordPolicyValidator.validateFormat(newTemporaryPassword, target.loginId());
        String canonicalRequest = canonicalRequest(
                actor, RESET_PASSWORD_OPERATION, targetEmployeeId.toString(), newTemporaryPassword);

        return idempotencyService.execute(
                actor.tenantId(), actor.employeeId(), RESET_PASSWORD_OPERATION, idempotencyKey, canonicalRequest,
                EmployeeIdentitySnapshot::serialize, EmployeeIdentitySnapshot::deserialize,
                () -> {
                    passwordPolicyValidator.validateNotReusingCurrent(newTemporaryPassword, target.passwordHash());
                    String hash = passwordEncoder.encode(newTemporaryPassword);
                    EmployeeAccount updated = employeeAccountRepository.save(target.resetPassword(hash, clock.instant()));
                    securityHistoryRecorder.recordPasswordReset(
                            actor.tenantId(), actor.storeId(), actor.employeeId(), target.id());
                    revokeSessionsAfterCommit(actor.tenantId(), target.id());
                    return EmployeeIdentitySnapshot.from(updated);
                });
    }

    /** Controller-facing overload of {@link #changeOwnPassword(ActorContext, String, String)}. */
    @Transactional
    public EmployeeSummary changeOwnPasswordForController(
            ActorContext actor, String currentPasswordRaw, String newPasswordRaw) {
        return EmployeeSummary.from(changeOwnPassword(actor, currentPasswordRaw, newPasswordRaw));
    }

    /** Self-service password change (ADR-02-009): not idempotency-wrapped — only creation and admin reset are. */
    @Transactional
    public EmployeeAccount changeOwnPassword(ActorContext actor, String currentPasswordRaw, String newPasswordRaw) {
        EmployeeAccount self = employeeAccountRepository.findById(actor.employeeId())
                .filter(account -> account.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));
        String currentPassword = PasswordPolicyValidator.normalize(currentPasswordRaw);
        if (!passwordEncoder.matches(currentPassword, self.passwordHash())) {
            throw new ProblemAwareException(
                    PasswordManagementProblemCode.CURRENT_PASSWORD_INCORRECT, "현재 비밀번호가 일치하지 않습니다.");
        }
        String newPassword = PasswordPolicyValidator.normalize(newPasswordRaw);
        passwordPolicyValidator.validate(newPassword, self.loginId(), self.passwordHash());
        String hash = passwordEncoder.encode(newPassword);
        EmployeeAccount saved = employeeAccountRepository.save(self.changePassword(hash, clock.instant()));
        securityHistoryRecorder.recordPasswordChanged(actor.tenantId(), actor.storeId(), actor.employeeId());
        revokeSessionsAfterCommit(actor.tenantId(), actor.employeeId());
        return saved;
    }

    private EmployeeAccount requireManageableTarget(ActorContext actor, UUID targetEmployeeId) {
        EmployeeAccount target = employeeAccountRepository.findById(targetEmployeeId)
                .filter(candidate -> candidate.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));
        if (target.id().equals(actor.employeeId())) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.SELF_ACCOUNT_ADMIN_CHANGE_NOT_ALLOWED,
                    "자기 계정은 관리자 API로 변경할 수 없습니다.");
        }
        return target;
    }

    /**
     * OWNER may manage any role. MANAGER may only manage the {@code STAFF} role — both as the role a target
     * currently holds and as the role being assigned. STAFF may not manage anyone.
     */
    private void requireCanManage(ActorContext actor, Role affectedRole) {
        boolean allowed = switch (actor.role()) {
            case OWNER -> true;
            case MANAGER -> affectedRole == Role.STAFF;
            case STAFF -> false;
        };
        if (!allowed) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN, "직원 관리 권한이 없습니다.");
        }
    }

    /** STAFF may not use any employee-management/query feature (기능 명세 §2). */
    private void requireCanView(ActorContext actor) {
        if (actor.role() == Role.STAFF) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN, "직원 조회 권한이 없습니다.");
        }
    }

    private LoginId parseLoginId(String rawLoginId) {
        try {
            return LoginId.normalize(rawLoginId);
        } catch (IllegalArgumentException e) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.INVALID_LOGIN_ID, "로그인 ID 형식이 올바르지 않습니다.");
        }
    }

    private void requireNotLastActiveOwner(UUID tenantId, EmployeeAccount target) {
        List<EmployeeAccount> activeOwners = employeeAccountRepository.findActiveOwnersForUpdate(tenantId);
        boolean isLastActiveOwner = activeOwners.size() <= 1
                && activeOwners.stream().anyMatch(owner -> owner.id().equals(target.id()));
        if (isLastActiveOwner) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.LAST_ACTIVE_OWNER_REQUIRED, "마지막 활성 OWNER는 변경할 수 없습니다.");
        }
    }

    /**
     * ADR-02-002/004: Redis Session deletion must happen only after the PostgreSQL Transaction that changed
     * Role/status/Credentials has actually committed, and a deletion failure must never roll that change
     * back. Deferring via {@link TransactionSynchronization#afterCommit()} — rather than calling
     * {@link EmployeeSessionRevoker} directly here — achieves exactly that ordering, since this method itself
     * still runs inside the caller's {@code @Transactional} boundary.
     */
    private void revokeSessionsAfterCommit(UUID tenantId, UUID employeeId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            employeeSessionRevoker.revokeAllSessions(tenantId, employeeId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                employeeSessionRevoker.revokeAllSessions(tenantId, employeeId);
            }
        });
    }

    private String canonicalRequest(ActorContext actor, String operation, String... parts) {
        StringBuilder canonical = new StringBuilder()
                .append(actor.tenantId()).append('|')
                .append(actor.employeeId()).append('|')
                .append(operation);
        for (String part : parts) {
            canonical.append('|').append(part);
        }
        return canonical.toString();
    }
}
