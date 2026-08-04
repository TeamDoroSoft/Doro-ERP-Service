package com.dorosoft.erp.identity.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.identity.application.authentication.DeniedPrivacyOperation;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessDeniedRecorder;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessContextFactory;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

class IdentityDeniedPrivacyAccessTest {
    private final IdentityPrivacyAccessDeniedRecorder recorder =
            mock(IdentityPrivacyAccessDeniedRecorder.class);
    private final TrustedClientIpResolver resolver = mock(TrustedClientIpResolver.class);
    private final IdentityPrivacyAccessContextFactory contextFactory =
            mock(IdentityPrivacyAccessContextFactory.class);
    private final PrivacyAccessContext encryptedContext = mock(PrivacyAccessContext.class);
    private IdentityDeniedPrivacyAccess support;

    @BeforeEach
    void setUp() {
        support = new IdentityDeniedPrivacyAccess(
                recorder, contextFactory, resolver,
                Clock.fixed(Instant.parse("2026-08-04T04:00:00Z"), ZoneOffset.UTC));
        when(resolver.resolveLiteral(any())).thenReturn("192.0.2.20");
        when(contextFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(encryptedContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedEmployeeListDenialRecordsSubjectlessRead() {
        authenticate();
        MockHttpServletRequest request = request("GET", "/api/v1/employees");

        support.recordIfRequired(request);

        verify(recorder).record(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_READ, encryptedContext);
    }

    @Test
    void roleAndPermissionEndpointsAreNotPrivacyTargets() {
        authenticate();

        support.recordIfRequired(request("GET", "/api/v1/roles"));
        support.recordIfRequired(request("PUT", "/api/v1/roles/EMPLOYEE/permissions"));
        support.recordIfRequired(request("GET", "/api/v1/permissions"));

        verifyNoInteractions(recorder);
    }

    @Test
    void unauthenticatedDenialDoesNotCreateAPrivacyRecord() {
        support.recordIfRequired(request("GET", "/api/v1/employees"));
        verifyNoInteractions(recorder);
    }

    @Test
    void auditHistoryDenialUsesTheDistinctPrivacyResourceContract() {
        authenticate();

        support.recordIfRequired(request("GET", "/api/v1/identity/audit-events"));

        verify(recorder).record(DeniedPrivacyOperation.IDENTITY_AUDIT_EVENT_READ, encryptedContext);
    }

    private void authenticate() {
        IdentityPrincipal principal = new IdentityPrincipal(
                UUID.randomUUID(), "tenant-a", "EMPLOYEE", Set.of("order.read"), false);
        SecurityContextHolder.getContext().setAuthentication(new IdentityAuthentication(principal));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute("doro.erp.requestId", "req-1");
        return request;
    }
}
