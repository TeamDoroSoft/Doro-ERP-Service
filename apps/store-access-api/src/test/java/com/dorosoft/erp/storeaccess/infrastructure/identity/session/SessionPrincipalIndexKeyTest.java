package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionPrincipalIndexKeyTest {

    @Test
    void formatsAsEmployeePrefixWithLowercaseTenantAndEmployeeIds() {
        UUID tenantId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        UUID employeeId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        SessionPrincipalIndexKey key = new SessionPrincipalIndexKey(tenantId, employeeId);

        assertThat(key.value())
                .isEqualTo("employee:3fa85f64-5717-4562-b3fc-2c963f66afa6:11111111-2222-3333-4444-555555555555");
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> new SessionPrincipalIndexKey(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullEmployeeId() {
        assertThatThrownBy(() -> new SessionPrincipalIndexKey(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
