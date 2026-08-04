package com.dorosoft.erp.identity.application.account;

import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.AccountStateResult;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.CreateEmployeeResult;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.EmployeeItem;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.EmployeePage;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.idempotency.CanonicalIdentityRequest;
import com.dorosoft.erp.identity.application.idempotency.IdentityIdempotencyCoordinator;
import com.dorosoft.erp.identity.application.idempotency.IdempotencyUnavailableException;
import com.dorosoft.erp.identity.application.port.CredentialRepository;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdentityIdempotencyRepository;
import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.application.port.PasswordHasher;
import com.dorosoft.erp.identity.application.port.RoleRepository;
import com.dorosoft.erp.identity.application.session.SessionRevoker;
import com.dorosoft.erp.identity.domain.account.AccountStatus;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.credential.Credential;
import com.dorosoft.erp.identity.domain.credential.PasswordHistoryEntry;
import com.dorosoft.erp.identity.domain.credential.PasswordPolicy;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.role.AssignmentSource;
import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import tools.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional implementation behind the account API boundary. */
@Service
public class EmployeeAccountTransactions {
    private final EmployeeAccountRepository accounts;
    private final CredentialRepository credentials;
    private final RoleRepository roles;
    private final PasswordHasher passwordHasher;
    private final IdentitySecurityEventRepository securityEvents;
    private final SessionRevoker sessionRevoker;
    private final IdentityAuditRecorder recorder;
    private final IdentityIdempotencyCoordinator idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EmployeeAccountTransactions(
            EmployeeAccountRepository accounts,
            CredentialRepository credentials,
            RoleRepository roles,
            PasswordHasher passwordHasher,
            IdentitySecurityEventRepository securityEvents,
            SessionRevoker sessionRevoker,
            IdentityAuditRecorder recorder,
            IdentityIdempotencyRepository idempotencyRepository,
            IdempotencyCryptoPort idempotencyCrypto,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.roles = Objects.requireNonNull(roles);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.securityEvents = Objects.requireNonNull(securityEvents);
        this.sessionRevoker = Objects.requireNonNull(sessionRevoker);
        this.recorder = Objects.requireNonNull(recorder);
        this.idempotency = new IdentityIdempotencyCoordinator(idempotencyRepository, idempotencyCrypto);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public CreateEmployeeResult create(
            EmployeeAccountApplicationService.ValidatedCreateEmployee command,
            String idempotencyKey,
            IdentityOperationContext context
    ) {
        Instant now = clock.instant();
        String normalizedPassword = Normalizer.normalize(command.initialPassword(), Normalizer.Form.NFC);
        byte[] canonical = CanonicalIdentityRequest.encode(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE,
                context.actorAccountId(), command.loginId(), command.displayName(),
                normalizedPassword, command.roleCode());
        try {
            var claim = idempotency.claim(
                    IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE,
                    idempotencyKey, canonical, context.actorAccountId(), now);
            if (claim instanceof IdentityIdempotencyCoordinator.Reused) {
                throw new IdentityException(IdentityErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (claim instanceof IdentityIdempotencyCoordinator.Replay replay) {
                CreateEmployeeResult result = readCreateProjection(replay.responseProjection());
            recorder.privacyRead(context, true,
                        List.of(result.accountId()));
                return result;
            }

            var fresh = (IdentityIdempotencyCoordinator.Fresh) claim;
            Role role = roles.findByCode(command.roleCode())
                    .filter(Role::active)
                    .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_ROLE));
            if (accounts.findByNormalizedLoginIdForUpdate(command.loginId()).isPresent()) {
                throw new IdentityException(IdentityErrorCode.LOGIN_ID_DUPLICATE);
            }
            String approvedPassword = PasswordPolicy.normalizeAndValidate(command.initialPassword());
            UUID accountId = UUID.randomUUID();
            EmployeeAccount account = accounts.save(EmployeeAccount.bootstrapActiveAccount(
                    accountId, command.loginId(), command.displayName()));
            Credential credential = credentials.save(Credential.initial(
                    accountId, passwordHasher.hash(approvedPassword), now));
            roles.saveAssignment(new RoleAssignment(
                    accountId, role.roleId(), role.code(), context.actorAccountId(), AssignmentSource.ADMIN, now));

            List<String> permissionCodes = sorted(role.permissionCodes());
            recorder.accountCreated(
                    context, actorDisplayName(context), accountId, role.roleId(),
                    account.accountStatus().name(), role.code().value(), permissionCodes);
            recorder.privacyCreate(context, true, List.of(accountId));

            CreateEmployeeResult result = new CreateEmployeeResult(
                    accountId, account.loginIdNormalized(), account.displayName(), account.accountStatus(),
                    role.code().value(), credential.mustChangePassword(), account.version());
            idempotency.complete201(fresh, accountId, writeCreateProjection(result), now);
            return result;
        } catch (IdempotencyUnavailableException exception) {
            throw new IdentityException(IdentityErrorCode.IDEMPOTENCY_UNAVAILABLE, exception);
        } finally {
            Arrays.fill(canonical, (byte) 0);
        }
    }

    @Transactional
    public EmployeePage list(
            EmployeeAccountApplicationService.ValidatedEmployeeFilter filter,
            IdentityOperationContext context
    ) {
        List<EmployeeItem> matched = new ArrayList<>();
        for (EmployeeAccount account : accounts.findAllOrderedById()) {
            if (filter.cursor() != null
                    && account.employeeAccountId().toString().compareTo(filter.cursor().toString()) <= 0) {
                continue;
            }
            RoleAssignment assignment = roles.findAssignment(account.employeeAccountId())
                    .orElseThrow(() -> new IllegalStateException("employee role assignment is missing"));
            if (filter.status() != null && account.accountStatus() != filter.status()
                    || filter.lockStatus() != null && account.loginLockStatus() != filter.lockStatus()
                    || filter.roleCode() != null && !filter.roleCode().equals(assignment.roleCode().value())
                    || filter.query() != null && !matchesQuery(account, filter.query())) {
                continue;
            }
            Credential credential = credentials.findByAccountId(account.employeeAccountId())
                    .orElseThrow(() -> new IllegalStateException("employee credential is missing"));
            matched.add(item(account, assignment, credential));
            if (matched.size() > filter.size()) {
                break;
            }
        }
        boolean hasNext = matched.size() > filter.size();
        List<EmployeeItem> items = hasNext ? List.copyOf(matched.subList(0, filter.size())) : List.copyOf(matched);
        String nextCursor = hasNext
                ? EmployeeAccountApplicationService.encodeCursor(items.getLast().accountId()) : null;
        recorder.privacyRead(context, true,
                items.stream().map(EmployeeItem::accountId).toList());
        return new EmployeePage(items, nextCursor);
    }

    @Transactional
    public AccountStateResult changeStatus(
            UUID accountId,
            long expectedVersion,
            AccountStatus status,
            IdentityOperationContext context
    ) {
        Objects.requireNonNull(status, "status");
        EmployeeAccount before = requireAccountForUpdate(accountId);
        IfMatchVersion.verify(expectedVersion, before.version());
        if (before.accountStatus() == status) {
            recorder.privacyUpdate(context, true, List.of(accountId));
            return state(before);
        }
        if (status == AccountStatus.INACTIVE) {
            sessionRevoker.revokeAll(accountId);
        }
        EmployeeAccount after = accounts.save(before.withAccountStatus(status));
        recorder.accountStatusChanged(
                context, actorDisplayName(context), accountId,
                before.accountStatus().name(), after.accountStatus().name());
        recorder.privacyUpdate(context, true, List.of(accountId));
        return state(after);
    }

    @Transactional
    public AccountStateResult unlock(
            UUID accountId,
            long expectedVersion,
            EmployeeAccountApplicationService.UnlockCommand command,
            IdentityOperationContext context
    ) {
        String reason = requireReason(command == null ? null : command.reason());
        EmployeeAccount before = requireAccountForUpdate(accountId);
        IfMatchVersion.verify(expectedVersion, before.version());
        if (before.loginLockStatus() == com.dorosoft.erp.identity.domain.account.LoginLockStatus.NONE) {
            recorder.privacyUpdate(context, true, List.of(accountId));
            return state(before);
        }
        sessionRevoker.revokeAll(accountId);
        EmployeeAccount after = accounts.save(before.withAdministratorUnlock());
        recorder.accountUnlocked(
                context, actorDisplayName(context), accountId, lockAudit(before), lockAudit(after), reason);
        recorder.privacyUpdate(context, true, List.of(accountId));
        return state(after);
    }

    @Transactional
    public void changeOwnPassword(
            EmployeeAccountApplicationService.ChangeOwnPasswordCommand command,
            IdentityOperationContext context
    ) {
        if (command == null || command.currentPassword() == null || command.newPassword() == null) {
            throw new IllegalArgumentException("current and new passwords are required");
        }
        UUID accountId = context.actorAccountId();
        EmployeeAccount account = requireAccountForUpdate(accountId);
        Credential credential = credentials.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("employee credential is missing"));
        String current = Normalizer.normalize(command.currentPassword(), Normalizer.Form.NFC);
        if (!passwordHasher.matches(current, credential.passwordHash())) {
            throw new IdentityException(IdentityErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        String next = PasswordPolicy.normalizeAndValidate(command.newPassword());
        validateNotReused(accountId, credential, next);
        credentials.addCurrentHashToHistoryAndKeepLatestFour(history(credential, clock.instant()));
        credentials.save(credential.changePassword(passwordHasher.hash(next), clock.instant()));
        accounts.incrementVersion(accountId, account.version());
        sessionRevoker.revokeAll(accountId);
        securityEvents.appendIfAbsent(successEvent(
                accountId, SecurityEventType.PASSWORD_CHANGED, context.requestId(), clock.instant()));
        recorder.privacyUpdate(context, true, List.of(accountId));
    }

    @Transactional
    public boolean resetPassword(
            UUID accountId,
            long expectedVersion,
            String rawIfMatch,
            String idempotencyKey,
            EmployeeAccountApplicationService.ResetPasswordCommand command,
            IdentityOperationContext context
    ) {
        if (command == null || command.temporaryPassword() == null) {
            throw new IllegalArgumentException("temporary password is required");
        }
        Instant now = clock.instant();
        String normalizedPassword = Normalizer.normalize(command.temporaryPassword(), Normalizer.Form.NFC);
        byte[] canonical = CanonicalIdentityRequest.encode(
                IdempotencyOperation.EMPLOYEE_PASSWORD_RESET, context.actorAccountId(),
                accountId, rawIfMatch, normalizedPassword);
        try {
            var claim = idempotency.claim(
                    IdempotencyOperation.EMPLOYEE_PASSWORD_RESET,
                    idempotencyKey, canonical, context.actorAccountId(), now);
            if (claim instanceof IdentityIdempotencyCoordinator.Reused) {
                throw new IdentityException(IdentityErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (claim instanceof IdentityIdempotencyCoordinator.Replay replay) {
                recorder.privacyRead(context, true,
                        List.of(replay.record().targetAccountId()));
                return true;
            }

            var fresh = (IdentityIdempotencyCoordinator.Fresh) claim;
            EmployeeAccount account = requireAccountForUpdate(accountId);
            IfMatchVersion.verify(expectedVersion, account.version());
            Credential credential = credentials.findByAccountIdForUpdate(accountId)
                    .orElseThrow(() -> new IllegalStateException("employee credential is missing"));
            String next = PasswordPolicy.normalizeAndValidate(command.temporaryPassword());
            validateNotReused(accountId, credential, next);
            credentials.addCurrentHashToHistoryAndKeepLatestFour(history(credential, now));
            credentials.save(credential.resetPassword(passwordHasher.hash(next), now));
            accounts.incrementVersion(accountId, account.version());
            sessionRevoker.revokeAll(accountId);
            securityEvents.appendIfAbsent(successEvent(
                    accountId, SecurityEventType.PASSWORD_RESET, context.requestId(), now));
            recorder.privacyUpdate(context, true, List.of(accountId));
            idempotency.complete204(fresh, accountId, now);
            return false;
        } catch (IdempotencyUnavailableException exception) {
            throw new IdentityException(IdentityErrorCode.IDEMPOTENCY_UNAVAILABLE, exception);
        } finally {
            Arrays.fill(canonical, (byte) 0);
        }
    }

    private void validateNotReused(UUID accountId, Credential credential, String normalizedPassword) {
        List<String> previous = credentials.findRecentHistory(accountId).stream()
                .map(PasswordHistoryEntry::passwordHash)
                .toList();
        PasswordPolicy.validateNotReused(
                normalizedPassword, credential.passwordHash(), previous, passwordHasher);
    }

    private static PasswordHistoryEntry history(Credential credential, Instant now) {
        return new PasswordHistoryEntry(
                UUID.randomUUID(), credential.employeeAccountId(), credential.passwordHash(), now);
    }

    private EmployeeAccount requireAccountForUpdate(UUID accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }

    private String actorDisplayName(IdentityOperationContext context) {
        return accounts.findById(context.actorAccountId())
                .map(EmployeeAccount::displayName)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED));
    }

    private static boolean matchesQuery(EmployeeAccount account, String query) {
        return account.loginIdNormalized().contains(query)
                || account.displayName().toLowerCase(Locale.ROOT).contains(query);
    }

    private static EmployeeItem item(
            EmployeeAccount account, RoleAssignment assignment, Credential credential) {
        return new EmployeeItem(
                account.employeeAccountId(), account.loginIdNormalized(), account.displayName(),
                account.accountStatus(), account.loginLockStatus(), account.failedLoginCount(),
                account.lockedUntil(), assignment.roleCode().value(),
                credential.mustChangePassword(), account.version());
    }

    private static AccountStateResult state(EmployeeAccount account) {
        return new AccountStateResult(
                account.employeeAccountId(), account.accountStatus(), account.loginLockStatus(),
                account.failedLoginCount(), account.temporaryLockCount(), account.lockedAt(),
                account.lockedUntil(), account.version());
    }

    private static Map<String, Object> lockAudit(EmployeeAccount account) {
        return map(
                "lockStatus", account.loginLockStatus().name(),
                "failedLoginCount", account.failedLoginCount(),
                "temporaryLockCount", account.temporaryLockCount(),
                "lockedAt", instant(account.lockedAt()),
                "lockedUntil", instant(account.lockedUntil()),
                "version", account.version());
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String requireReason(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("unlock reason is required");
        }
        String value = raw.strip();
        if (value.isEmpty() || value.length() > 500) {
            throw new IllegalArgumentException("unlock reason must contain 1 to 500 characters");
        }
        return value;
    }

    private static IdentitySecurityEvent successEvent(
            UUID accountId, SecurityEventType type, String requestId, Instant occurredAt) {
        return new IdentitySecurityEvent(
                UUID.randomUUID(), accountId, type, SecurityEventOutcome.SUCCESS,
                null, requestId, occurredAt);
    }

    private byte[] writeCreateProjection(CreateEmployeeResult result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException("Create response could not be encrypted", exception);
        }
    }

    private CreateEmployeeResult readCreateProjection(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, CreateEmployeeResult.class);
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException("Stored create response could not be replayed", exception);
        }
    }

    private static List<String> sorted(Set<String> codes) {
        return codes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }
}
