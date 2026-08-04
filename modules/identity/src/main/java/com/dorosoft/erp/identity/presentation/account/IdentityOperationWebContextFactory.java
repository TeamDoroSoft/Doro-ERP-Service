package com.dorosoft.erp.identity.presentation.account;

import com.dorosoft.erp.identity.application.account.IdentityOperationContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * Runtime bridge for a verified session principal and encrypted trusted client address.
 * Implementations must never place a plaintext address in {@link IdentityOperationContext}.
 */
public interface IdentityOperationWebContextFactory {
    IdentityOperationContext create(Authentication authentication, HttpServletRequest request);
}
