package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.authentication.DeniedPrivacyOperation;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessDeniedRecorder;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessContextFactory;
import com.dorosoft.erp.identity.application.authentication.IdentityDeniedPrivacyAccessHandler;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Selects only the authenticated Feature 01 PII endpoints covered by the denial contract. */
@Component
public final class IdentityDeniedPrivacyAccess implements IdentityDeniedPrivacyAccessHandler {
    private final IdentityPrivacyAccessDeniedRecorder recorder;
    private final IdentityPrivacyAccessContextFactory contextFactory;
    private final TrustedClientIpResolver clientIpResolver;
    private final Clock clock;

    public IdentityDeniedPrivacyAccess(
            IdentityPrivacyAccessDeniedRecorder recorder,
            IdentityPrivacyAccessContextFactory contextFactory,
            TrustedClientIpResolver clientIpResolver,
            Clock clock
    ) {
        this.recorder = recorder;
        this.contextFactory = contextFactory;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
    }

    @Override
    public boolean recordIfRequired(HttpServletRequest request) {
        Optional<DeniedPrivacyOperation> operation = classify(request);
        if (operation.isEmpty()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return false;
        }
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        if (!(requestId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Request ID is unavailable for privacy denial recording");
        }
        var encryptedContext = contextFactory.create(
                principal.tenantKey(), principal.accountId(), principal.roleCode(), value,
                clientIpResolver.resolveLiteral(request), clock.instant());
        recorder.record(operation.orElseThrow(), encryptedContext);
        return true;
    }

    private Optional<DeniedPrivacyOperation> classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (request.getContextPath() != null && !request.getContextPath().isEmpty()
                && path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }
        if ("/api/v1/auth/me/password".equals(path)) {
            return Optional.of(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_UPDATE);
        }
        if ("/api/v1/identity/audit-events".equals(path)) {
            return Optional.of(DeniedPrivacyOperation.IDENTITY_AUDIT_EVENT_READ);
        }
        if ("/api/v1/employees".equals(path) || path.startsWith("/api/v1/employees/")) {
            if ("GET".equals(request.getMethod())) {
                return Optional.of(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_READ);
            }
            if ("POST".equals(request.getMethod()) && "/api/v1/employees".equals(path)) {
                return Optional.of(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_CREATE);
            }
            return Optional.of(DeniedPrivacyOperation.EMPLOYEE_ACCOUNT_UPDATE);
        }
        return Optional.empty();
    }
}
