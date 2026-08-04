package com.dorosoft.erp.identity.domain.role;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RolePermissionPolicyTest {
    @Test
    void permissionCatalogContainsExactlySixtyThreeCodes() {
        assertEquals(63, PermissionCatalog.all().size());
    }

    @Test
    void nonAdminCannotContainAdministratorOnlyPermissions() {
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForNonAdminRole(Set.of("identity.account.read"))
        );
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForNonAdminRole(Set.of("audit.read"))
        );
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForNonAdminRole(Set.of("reservation.policy.manage", "reservation.policy.read"))
        );
    }

    @Test
    void everyDocumentedDependencyIsEnforced() {
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForNonAdminRole(Set.of("payment.manual.complete"))
        );
        assertDoesNotThrow(() -> RolePermissionPolicy.validateForNonAdminRole(
                Set.of("payment.manual.complete", "order.read")
        ));
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForNonAdminRole(Set.of("sales.correct", "sales.closing.read"))
        );
        assertDoesNotThrow(() -> RolePermissionPolicy.validateForNonAdminRole(
                Set.of("sales.correct", "sales.closing.read", "order.read")
        ));
    }

    @Test
    void adminMustAlwaysEqualTheCompleteCatalog() {
        assertDoesNotThrow(() -> RolePermissionPolicy.validateForAdminRole(PermissionCatalog.all()));
        assertThrows(IllegalStateException.class, () ->
                RolePermissionPolicy.validateForAdminRole(Set.of("identity.account.read"))
        );
    }

    @Test
    void bootstrapRepresentativeRoleCannotBeDemotedButOtherAdminsAreAllowed() {
        RoleAssignment representative = new RoleAssignment(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), new RoleCode("ADMIN"),
                null, AssignmentSource.BOOTSTRAP, java.time.Instant.EPOCH
        );
        assertThrows(IllegalStateException.class, () -> representative.replaceRole(
                java.util.UUID.randomUUID(), new RoleCode("EMPLOYEE"),
                java.util.UUID.randomUUID(), java.time.Instant.EPOCH.plusSeconds(1)
        ));
        assertDoesNotThrow(() -> new RoleAssignment(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), new RoleCode("ADMIN"),
                java.util.UUID.randomUUID(), AssignmentSource.ADMIN, java.time.Instant.EPOCH
        ));
    }
}
