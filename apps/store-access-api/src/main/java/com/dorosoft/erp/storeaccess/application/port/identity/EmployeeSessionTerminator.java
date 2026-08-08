package com.dorosoft.erp.storeaccess.application.port.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ends only the current employee Session (ADR-02-002: general logout never touches the employee's other
 * Sessions — that is reserved for Role/status/password changes via {@code SessionInvalidator}).
 */
public interface EmployeeSessionTerminator {

    void terminateCurrentSession(HttpServletRequest request, HttpServletResponse response);
}
