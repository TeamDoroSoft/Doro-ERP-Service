package com.dorosoft.erp.audit.domain;

import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Feature17ContractTest {

    @Test
    void identityActionsAreContractedForFeature17Audit() {
        assertEquals(6, Feature17AuditContracts.identityActions().size());
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.EMPLOYEE_ACCOUNT_CREATED));
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.EMPLOYEE_ACCOUNT_ACTIVATED));
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.EMPLOYEE_ACCOUNT_DEACTIVATED));
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.ACCOUNT_LOGIN_UNLOCKED));
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.EMPLOYEE_PERMISSIONS_CHANGED));
        assertTrue(Feature17AuditContracts.identityActions().contains(AuditAction.ROLE_PERMISSIONS_CHANGED));
    }

    @Test
    void identityActionsRequireExpectedPrimaryTargets() {
        assertEquals(
                AuditTargetType.ACCOUNT,
                Feature17AuditContracts.primaryTargetFor(AuditAction.EMPLOYEE_ACCOUNT_CREATED)
        );
        assertEquals(
                AuditTargetType.ACCOUNT,
                Feature17AuditContracts.primaryTargetFor(AuditAction.EMPLOYEE_ACCOUNT_ACTIVATED)
        );
        assertEquals(
                AuditTargetType.ACCOUNT,
                Feature17AuditContracts.primaryTargetFor(AuditAction.EMPLOYEE_ACCOUNT_DEACTIVATED)
        );
        assertEquals(
                AuditTargetType.ACCOUNT,
                Feature17AuditContracts.primaryTargetFor(AuditAction.ACCOUNT_LOGIN_UNLOCKED)
        );
        assertEquals(
                AuditTargetType.ACCOUNT,
                Feature17AuditContracts.primaryTargetFor(AuditAction.EMPLOYEE_PERMISSIONS_CHANGED)
        );
        assertEquals(
                AuditTargetType.ROLE,
                Feature17AuditContracts.primaryTargetFor(AuditAction.ROLE_PERMISSIONS_CHANGED)
        );
    }

    @Test
    void mismatchIdentityTargetIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Feature17AuditContracts.ensureSupportedAction(
                        AuditAction.ROLE_PERMISSIONS_CHANGED,
                        AuditTargetType.ACCOUNT
                )
        );
    }

    @Test
    void privacyRegistryHasRequiredIdentityCodes() {
        assertTrue(Feature17PrivacyContracts.isAllowedResourceType(PrivacyResourceType.EMPLOYEE_ACCOUNT));
        assertTrue(Feature17PrivacyContracts.isAllowedResourceType(PrivacyResourceType.IDENTITY_AUDIT_EVENT));
        assertTrue(Feature17PrivacyContracts.isAllowedResultCode(PrivacyResultCode.SUCCESS));
        assertTrue(Feature17PrivacyContracts.isAllowedResultCode(PrivacyResultCode.DENIED));
    }

    @Test
    void auditCommandSupportsSafeDefaults() {
        var command = AuditRecordCommand.builder()
                .domain(AuditDomain.IDENTITY)
                .action(AuditAction.EMPLOYEE_ACCOUNT_CREATED)
                .operationId(UUID.randomUUID())
                .eventSequence(0)
                .primaryTarget(new AuditPrimaryTarget(AuditTargetType.ACCOUNT, UUID.randomUUID()))
                .relatedTargets(List.of())
                .beforeValue(Map.of())
                .afterValue(Map.of(
                        "status", "ACTIVE",
                        "role", "ADMIN",
                        "permissionCodes", List.of("account.read")
                ))
                .reasonCode(null)
                .reason(null)
                .build();

        assertEquals(0, command.eventSequence());
        assertEquals(1, command.valueSchemaVersion());
    }

    @Test
    void identityFactoriesKeepRegistryTypesInsideTheAuditPublicApi() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        AuditQueryFilter filter = AuditQueryFilter.identity(
                AuditAction.ACCOUNT_LOGIN_UNLOCKED.name(), actor, target,
                java.time.Instant.parse("2026-08-01T00:00:00Z"),
                java.time.Instant.parse("2026-08-02T00:00:00Z"), 20, null);
        PrivacyAccessCommand privacy = PrivacyAccessCommand.identityAuditEventRead(
                List.of(new PrivacyAccessSubject("EMPLOYEE", target)));

        assertEquals(AuditDomain.IDENTITY, filter.domain());
        assertEquals(AuditTargetType.ACCOUNT, filter.targetType());
        assertEquals(target, filter.targetId());
        assertEquals(PrivacyResourceType.IDENTITY_AUDIT_EVENT, privacy.resourceType());
        assertEquals(PrivacyPurposeCode.SECURITY_INVESTIGATION, privacy.purposeCode());
        assertEquals(1, privacy.resultCount());
    }
}
