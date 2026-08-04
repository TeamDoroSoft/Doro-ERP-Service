package com.dorosoft.erp.identity.presentation.common;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.authentication.IdentityDeniedPrivacyAccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Handles method-security denials that occur inside DispatcherServlet rather than the filter chain. */
@RestControllerAdvice(basePackages = "com.dorosoft.erp.identity.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityMethodSecurityProblemAdvice {
    private final IdentityDeniedPrivacyAccessHandler deniedPrivacyAccess;

    public IdentityMethodSecurityProblemAdvice(IdentityDeniedPrivacyAccessHandler deniedPrivacyAccess) {
        this.deniedPrivacyAccess = deniedPrivacyAccess;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        if (exception instanceof CsrfException) {
            return problem(IdentityErrorCode.CSRF_TOKEN_INVALID, request);
        }
        try {
            deniedPrivacyAccess.recordIfRequired(request);
        } catch (RuntimeException privacyFailure) {
            return problem(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, request);
        }
        return problem(IdentityErrorCode.FORBIDDEN, request);
    }

    private ResponseEntity<ProblemDetail> problem(IdentityErrorCode code, HttpServletRequest request) {
        String requestId = IdentityRequestId.from(request);
        ProblemDetail detail = code.toProblemDetail(requestId, List.of(), code.defaultDetail());
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }
}
