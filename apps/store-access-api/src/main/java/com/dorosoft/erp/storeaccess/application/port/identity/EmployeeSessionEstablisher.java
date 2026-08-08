package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Creates the Redis-backed employee Session and Session cookie for a successful login (ADR-02-002/003/005).
 * A Port so the Controller — which may not depend on {@code infrastructure} — never touches Spring Session
 * or the Security Context directly; the real work happens in the {@code infrastructure} Adapter.
 */
public interface EmployeeSessionEstablisher {

    void establish(LoginResult result, HttpServletRequest request, HttpServletResponse response);
}
