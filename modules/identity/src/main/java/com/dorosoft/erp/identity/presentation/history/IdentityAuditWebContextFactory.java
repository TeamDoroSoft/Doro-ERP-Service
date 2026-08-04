package com.dorosoft.erp.identity.presentation.history;

import com.dorosoft.erp.identity.application.history.IdentityAuditAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/** Bridges the verified web principal and encrypted trusted address into the Application context. */
public interface IdentityAuditWebContextFactory {
    IdentityAuditAccessContext create(Authentication authentication, HttpServletRequest request);
}
