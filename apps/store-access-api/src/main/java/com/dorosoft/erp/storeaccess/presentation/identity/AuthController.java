package com.dorosoft.erp.storeaccess.presentation.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.EmployeeLoginService;
import com.dorosoft.erp.storeaccess.application.api.identity.LoginCommand;
import com.dorosoft.erp.storeaccess.application.api.identity.LoginResult;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionEstablisher;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionTerminator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Employee login/logout (ADR-02-002/003/006/007/008). Session persistence goes through Application Ports
 * ({@link EmployeeSessionEstablisher}/{@link EmployeeSessionTerminator}) since this Controller may not depend
 * on {@code infrastructure} directly. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final EmployeeLoginService employeeLoginService;
    private final EmployeeSessionEstablisher sessionEstablisher;
    private final EmployeeSessionTerminator sessionTerminator;

    AuthController(
            EmployeeLoginService employeeLoginService,
            EmployeeSessionEstablisher sessionEstablisher,
            EmployeeSessionTerminator sessionTerminator) {
        this.employeeLoginService = employeeLoginService;
        this.sessionEstablisher = sessionEstablisher;
        this.sessionTerminator = sessionTerminator;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        LoginResult result = employeeLoginService.login(new LoginCommand(
                request.tenantCode(), request.loginId(), request.password(), servletRequest.getRemoteAddr()));
        sessionEstablisher.establish(result, servletRequest, servletResponse);
        return ResponseEntity.ok(
                new LoginResponse(result.employeeId(), result.roleName(), result.passwordChangeRequired()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        sessionTerminator.terminateCurrentSession(servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }
}
