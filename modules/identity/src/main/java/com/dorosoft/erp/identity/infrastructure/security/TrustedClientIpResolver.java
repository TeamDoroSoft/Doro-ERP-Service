package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.application.authentication.IdentityClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Uses the address established by the servlet container's trusted proxy boundary. Arbitrary
 * forwarding headers are deliberately ignored here.
 */
@Component
public final class TrustedClientIpResolver implements IdentityClientAddressResolver {

    @Override
    public ClientIpAddress resolve(HttpServletRequest request) {
        return ClientIpAddress.of(resolveAddress(request));
    }

    @Override
    public String resolveLiteral(HttpServletRequest request) {
        return resolveAddress(request).getHostAddress();
    }

    private InetAddress resolveAddress(HttpServletRequest request) {
        try {
            return InetAddress.ofLiteral(request.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Trusted client address is unavailable", exception);
        }
    }
}
