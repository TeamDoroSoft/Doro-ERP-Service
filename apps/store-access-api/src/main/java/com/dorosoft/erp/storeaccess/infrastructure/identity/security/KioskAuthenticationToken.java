package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The Kiosk device {@code Authentication} (ADR-02-013), set directly on {@link org.springframework.security.core.context.SecurityContextHolder}
 * per request by {@link KioskDeviceAuthenticationFilter} — never stored in a Spring Session, since Kiosk
 * Cookie authentication is verified fresh against PostgreSQL on every request.
 */
public class KioskAuthenticationToken extends AbstractAuthenticationToken {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_KIOSK_DEVICE"));

    private final KioskPrincipal principal;

    public KioskAuthenticationToken(KioskPrincipal principal) {
        super(AUTHORITIES);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public KioskPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return "kiosk:" + principal.tenantId() + ":" + principal.kioskDeviceId();
    }
}
