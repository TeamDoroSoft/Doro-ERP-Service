package com.dorosoft.erp.audit.domain;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.usecase.AuditRecordValidator;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditRecordValidatorTest {
    private final AuditRecordValidator validator = new AuditRecordValidator(new ObjectMapper());
    private final UUID accountId = UUID.randomUUID();

    @Test
    void acceptsIdentityAccountCreationSchema() {
        assertDoesNotThrow(() -> validator.validate(created(Map.of(
                "status", "ACTIVE",
                "role", "ADMIN",
                "permissionCodes", List.of("account.read")
        )), context()));
    }

    @Test
    void rejectsUnknownAndSensitiveFieldsWithoutEchoingTheirValues() {
        AuditContractException exception = assertThrows(AuditContractException.class,
                () -> validator.validate(created(Map.of(
                        "status", "ACTIVE", "role", "ADMIN", "permissionCodes", List.of(),
                        "passwordHash", "must-never-appear"
                )), context()));

        assertEquals(AuditErrorCode.AUDIT_SENSITIVE_VALUE_REJECTED, exception.code());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("must-never-appear"));
    }

    @Test
    void rejectsSensitiveReasonPatternsWithoutEchoingTheInput() {
        AuditRecordCommand command = new AuditRecordCommand(
                AuditDomain.IDENTITY, AuditAction.ACCOUNT_LOGIN_UNLOCKED, UUID.randomUUID(), 0,
                new AuditPrimaryTarget(AuditTargetType.ACCOUNT, accountId), List.of(),
                lockValue("TEMPORARY", 5, 1), lockValue("NONE", 0, 0), null,
                "문의: user@example.com", 1);

        AuditContractException exception = assertThrows(AuditContractException.class,
                () -> validator.validate(command, context()));
        assertEquals(AuditErrorCode.AUDIT_SENSITIVE_VALUE_REJECTED, exception.code());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("user@example.com"));
    }

    @Test
    void permitsNullLockTimestampsInRegisteredProjection() {
        assertDoesNotThrow(() -> validator.validate(new AuditRecordCommand(
                AuditDomain.IDENTITY, AuditAction.ACCOUNT_LOGIN_UNLOCKED, UUID.randomUUID(), 0,
                new AuditPrimaryTarget(AuditTargetType.ACCOUNT, accountId), List.of(),
                lockValue("PERMANENT", 10, 2), lockValue("NONE", 0, 0), null,
                "관리자 확인 후 잠금 해제", 1), context()));
    }

    private AuditRecordCommand created(Map<String, Object> after) {
        return new AuditRecordCommand(
                AuditDomain.IDENTITY, AuditAction.EMPLOYEE_ACCOUNT_CREATED, UUID.randomUUID(), 0,
                new AuditPrimaryTarget(AuditTargetType.ACCOUNT, accountId), List.of(), Map.of(), after,
                null, null, 1);
    }

    private Map<String, Object> lockValue(String status, int failed, int temporary) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("lockStatus", status);
        value.put("failedLoginCount", failed);
        value.put("temporaryLockCount", temporary);
        value.put("lockedAt", null);
        value.put("lockedUntil", null);
        value.put("version", 1);
        return value;
    }

    private AuditContext context() {
        return new AuditContext(
                "local-store", ActorType.ADMIN, UUID.randomUUID(), "ADMIN", "관리자", "req-test",
                Instant.parse("2026-08-04T10:00:00Z"));
    }
}
