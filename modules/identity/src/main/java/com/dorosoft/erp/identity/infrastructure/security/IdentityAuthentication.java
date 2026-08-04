package com.dorosoft.erp.identity.infrastructure.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class IdentityAuthentication extends AbstractAuthenticationToken {

    private final IdentityPrincipal principal;

    public IdentityAuthentication(IdentityPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public IdentityPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.getName();
    }

    private static List<SimpleGrantedAuthority> authorities(IdentityPrincipal principal) {
        List<String> codes = new ArrayList<>(principal.permissions());
        codes.sort(Comparator.naturalOrder());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(codes.size() + 1);
        codes.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.roleCode()));
        return List.copyOf(authorities);
    }
}
