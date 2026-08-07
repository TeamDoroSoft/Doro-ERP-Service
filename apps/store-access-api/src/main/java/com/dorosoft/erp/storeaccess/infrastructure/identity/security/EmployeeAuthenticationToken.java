package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.infrastructure.identity.session.SessionPrincipalIndexKey;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The employee {@code Authentication} stored in the Session (ADR-02-002/005). {@link #getName()} returns the
 * exact {@code employee:{tenantId}:{employeeId}} Session Principal Index Key format so that Spring Session
 * Data Redis's automatic {@code SPRING_SECURITY_CONTEXT}-based indexing registers Sessions under that key
 * with no separate manual indexing step.
 */
public class EmployeeAuthenticationToken extends AbstractAuthenticationToken {

    private final EmployeePrincipal principal;

    public EmployeeAuthenticationToken(EmployeePrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(EmployeePrincipal principal) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public EmployeePrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return new SessionPrincipalIndexKey(principal.tenantId(), principal.employeeId()).value();
    }
}
