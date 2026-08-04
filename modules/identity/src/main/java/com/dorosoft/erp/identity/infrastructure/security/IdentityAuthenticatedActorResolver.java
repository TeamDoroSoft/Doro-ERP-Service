package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.authentication.AuthenticatedActor;
import com.dorosoft.erp.identity.application.authentication.AuthenticatedActorResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class IdentityAuthenticatedActorResolver implements AuthenticatedActorResolver {
    @Override
    public AuthenticatedActor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return null;
        }
        return new AuthenticatedActor(
                principal.accountId(), principal.tenantKey(), principal.roleCode(),
                principal.permissions(), principal.mustChangePassword());
    }
}
