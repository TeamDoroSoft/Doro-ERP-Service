package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import jakarta.servlet.http.HttpServletRequest;

public interface IdentityClientAddressResolver {
    ClientIpAddress resolve(HttpServletRequest request);

    String resolveLiteral(HttpServletRequest request);
}
