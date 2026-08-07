package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.api.identity.ActorContext;
import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.application.port.identity.CurrentActorResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the {@link EmployeePrincipal} {@link EmployeeSessionAuthenticationFilter} placed into
 * {@link SecurityContextHolder} for this request (ADR-02-002). Every path reaching this bean sits behind
 * {@code anyRequest().authenticated()}, so a missing/wrong-typed Authentication here is a genuine
 * configuration bug, not a normal Client error — it still fails safely rather than with an NPE.
 */
@Component
class SecurityContextCurrentActorResolver implements CurrentActorResolver {

    @Override
    public ActorContext currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof EmployeeAuthenticationToken token)) {
            throw new ProblemAwareException(AuthProblemCode.UNAUTHENTICATED, "로그인이 필요합니다.");
        }
        EmployeePrincipal principal = token.getPrincipal();
        return new ActorContext(principal.tenantId(), principal.storeId(), principal.employeeId(), principal.role());
    }
}
