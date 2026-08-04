package com.dorosoft.erp.identity.application.account;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.domain.account.AccountStatus;
import com.dorosoft.erp.identity.domain.account.DisplayName;
import com.dorosoft.erp.identity.domain.account.LoginId;
import com.dorosoft.erp.identity.domain.account.LoginLockStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Non-transactional boundary used so rejected requests can append their DENIED privacy record. */
@Service
public final class EmployeeAccountApplicationService {
    private final EmployeeAccountTransactions transactions;
    private final IdentityAuditRecorder recorder;

    public EmployeeAccountApplicationService(
            EmployeeAccountTransactions transactions,
            IdentityAuditRecorder recorder
    ) {
        this.transactions = transactions;
        this.recorder = recorder;
    }

    public CreateEmployeeResult create(
            CreateEmployeeCommand command,
            String idempotencyKey,
            IdentityOperationContext context
    ) {
        try {
            return transactions.create(validate(command), requireIdempotencyKey(idempotencyKey), context);
        } catch (IdentityException exception) {
            if (exception.code() == IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                throw exception;
            }
            recorder.privacyCreate(context, false, List.of());
            throw exception;
        } catch (IllegalArgumentException exception) {
            recorder.privacyCreate(context, false, List.of());
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED, exception);
        }
    }

    public EmployeePage list(EmployeeFilter filter, IdentityOperationContext context) {
        try {
            EmployeePage result = transactions.list(validate(filter), context);
            return result;
        } catch (IdentityException exception) {
            if (exception.code() == IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                throw exception;
            }
            recorder.privacyRead(context, false, List.of());
            throw exception;
        } catch (IllegalArgumentException exception) {
            recorder.privacyRead(context, false, List.of());
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED, exception);
        }
    }

    public AccountStateResult changeStatus(
            UUID accountId,
            String ifMatch,
            ChangeStatusCommand command,
            IdentityOperationContext context
    ) {
        final AccountStatus status;
        try {
            status = parseAccountStatus(command);
        } catch (IllegalArgumentException exception) {
            recorder.privacyUpdate(context, false, List.of());
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED, exception);
        }
        long expectedVersion = parseIfMatch(ifMatch, context);
        return targetMutation(accountId, context,
                () -> transactions.changeStatus(accountId, expectedVersion, status, context));
    }

    public AccountStateResult unlock(
            UUID accountId,
            String ifMatch,
            UnlockCommand command,
            IdentityOperationContext context
    ) {
        long expectedVersion = parseIfMatch(ifMatch, context);
        return targetMutation(accountId, context,
                () -> transactions.unlock(accountId, expectedVersion, command, context));
    }

    public void changeOwnPassword(ChangeOwnPasswordCommand command, IdentityOperationContext context) {
        targetMutation(context.actorAccountId(), context, () -> {
            transactions.changeOwnPassword(command, context);
            return null;
        });
    }

    public boolean resetPassword(
            UUID accountId,
            String ifMatch,
            String idempotencyKey,
            ResetPasswordCommand command,
            IdentityOperationContext context
    ) {
        long expectedVersion = parseIfMatch(ifMatch, context);
        String approvedKey = requireTargetIdempotencyKey(idempotencyKey, context);
        return targetMutation(accountId, context, () -> transactions.resetPassword(
                accountId, expectedVersion, ifMatch,
                approvedKey, command, context));
    }

    private <T> T targetMutation(UUID targetId, IdentityOperationContext context, Operation<T> operation) {
        try {
            return operation.run();
        } catch (IdentityException exception) {
            if (exception.code() == IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                throw exception;
            }
            List<UUID> subjects = targetWasNotConfirmed(exception)
                    ? List.of() : List.of(targetId);
            recorder.privacyUpdate(context, false, subjects);
            throw exception;
        } catch (IllegalArgumentException exception) {
            recorder.privacyUpdate(context, false, List.of(targetId));
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED, exception);
        }
    }

    private static boolean targetWasNotConfirmed(IdentityException exception) {
        return exception.code() == IdentityErrorCode.ACCOUNT_NOT_FOUND
                || exception.code() == IdentityErrorCode.IDEMPOTENCY_KEY_REUSED
                || exception.code() == IdentityErrorCode.IDEMPOTENCY_UNAVAILABLE
                || exception.code() == IdentityErrorCode.IDEMPOTENCY_KEY_REQUIRED
                || exception.code() == IdentityErrorCode.PRECONDITION_REQUIRED;
    }

    private long parseIfMatch(String ifMatch, IdentityOperationContext context) {
        try {
            return IfMatchVersion.parse(ifMatch);
        } catch (IdentityException exception) {
            recorder.privacyUpdate(context, false, List.of());
            throw exception;
        }
    }

    private String requireTargetIdempotencyKey(String value, IdentityOperationContext context) {
        try {
            return requireIdempotencyKey(value);
        } catch (IdentityException | IllegalArgumentException exception) {
            recorder.privacyUpdate(context, false, List.of());
            throw exception;
        }
    }

    private static ValidatedCreateEmployee validate(CreateEmployeeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("create command is required");
        }
        String loginId = LoginId.normalize(command.loginId()).value();
        String displayName = DisplayName.normalize(command.displayName()).value();
        if (command.initialPassword() == null || command.roleCode() == null || command.roleCode().isBlank()) {
            throw new IllegalArgumentException("initial password and role are required");
        }
        return new ValidatedCreateEmployee(
                loginId, displayName, command.initialPassword(), command.roleCode().strip().toUpperCase(Locale.ROOT));
    }

    private static AccountStatus parseAccountStatus(ChangeStatusCommand command) {
        if (command == null || command.status() == null || command.status().isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        try {
            return AccountStatus.valueOf(command.status().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status must be ACTIVE or INACTIVE", exception);
        }
    }

    private static ValidatedEmployeeFilter validate(EmployeeFilter filter) {
        EmployeeFilter safe = filter == null ? new EmployeeFilter(null, null, null, null, null, 20) : filter;
        if (safe.size() < 1 || safe.size() > 100) {
            throw new IllegalArgumentException("size must be 1 to 100");
        }
        AccountStatus status = parseEnum(AccountStatus.class, safe.status());
        LoginLockStatus lock = parseEnum(LoginLockStatus.class, safe.lockStatus());
        String role = safe.roleCode() == null || safe.roleCode().isBlank()
                ? null : safe.roleCode().strip().toUpperCase(Locale.ROOT);
        String query = safe.query() == null || safe.query().isBlank()
                ? null : safe.query().strip().toLowerCase(Locale.ROOT);
        UUID cursor = decodeCursor(safe.cursor());
        return new ValidatedEmployeeFilter(status, lock, role, query, cursor, safe.size());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported filter value", exception);
        }
    }

    private static UUID decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            return UUID.fromString(decoded);
        } catch (IllegalArgumentException exception) {
            throw new IdentityException(IdentityErrorCode.INVALID_CURSOR, exception);
        }
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IdentityException(IdentityErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 200 characters");
        }
        return value;
    }

    static String encodeCursor(UUID accountId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(accountId.toString().getBytes(StandardCharsets.US_ASCII));
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }

    public record CreateEmployeeCommand(
            String loginId, String displayName, String initialPassword, String roleCode) {
    }

    public record ChangeStatusCommand(String status) {
    }

    public record UnlockCommand(String reason) {
    }

    public record ChangeOwnPasswordCommand(String currentPassword, String newPassword) {
    }

    public record ResetPasswordCommand(String temporaryPassword) {
    }

    public record EmployeeFilter(
            String status, String lockStatus, String roleCode, String query, String cursor, int size) {
    }

    record ValidatedCreateEmployee(
            String loginId, String displayName, String initialPassword, String roleCode) {
    }

    record ValidatedEmployeeFilter(
            AccountStatus status,
            LoginLockStatus lockStatus,
            String roleCode,
            String query,
            UUID cursor,
            int size
    ) {
    }

    public record CreateEmployeeResult(
            UUID accountId,
            String loginId,
            String displayName,
            AccountStatus status,
            String roleCode,
            boolean mustChangePassword,
            long version
    ) {
    }

    public record EmployeeItem(
            UUID accountId,
            String loginId,
            String displayName,
            AccountStatus status,
            LoginLockStatus lockStatus,
            int failedLoginCount,
            java.time.Instant lockedUntil,
            String roleCode,
            boolean mustChangePassword,
            long version
    ) {
    }

    public record EmployeePage(List<EmployeeItem> items, String nextCursor) {
        public EmployeePage {
            items = List.copyOf(items);
        }
    }

    public record AccountStateResult(
            UUID accountId,
            AccountStatus status,
            LoginLockStatus lockStatus,
            int failedLoginCount,
            int temporaryLockCount,
            java.time.Instant lockedAt,
            java.time.Instant lockedUntil,
            long version
    ) {
    }
}
