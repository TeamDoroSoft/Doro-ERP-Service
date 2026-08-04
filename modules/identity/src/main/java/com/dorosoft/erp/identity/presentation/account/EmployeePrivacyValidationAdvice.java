package com.dorosoft.erp.identity.presentation.account;

import com.dorosoft.erp.identity.application.account.IdentityAuditRecorder;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.platform.web.GlobalProblemAdvice;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Records authenticated employee-PII request-shape denials before the shared RFC 9457 advice
 * renders its validation response. Privacy append failure takes precedence as the required 503.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        EmployeeAccountController.class,
        com.dorosoft.erp.identity.presentation.authorization.AuthorizationController.class
})
public final class EmployeePrivacyValidationAdvice extends GlobalProblemAdvice {
    private final IdentityOperationWebContextFactory contextFactory;
    private final IdentityAuditRecorder recorder;

    public EmployeePrivacyValidationAdvice(
            IdentityOperationWebContextFactory contextFactory,
            IdentityAuditRecorder recorder
    ) {
        this.contextFactory = contextFactory;
        this.recorder = recorder;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ResponseEntity<Object> unavailable = recordDeniedIfRequired(request);
        return unavailable != null
                ? unavailable
                : super.handleMethodArgumentNotValid(exception, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (status.is4xxClientError()) {
            ResponseEntity<Object> unavailable = recordDeniedIfRequired(request);
            if (unavailable != null) {
                return unavailable;
            }
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    private ResponseEntity<Object> recordDeniedIfRequired(WebRequest webRequest) {
        if (!(webRequest instanceof ServletWebRequest servletRequest)) {
            return null;
        }
        HttpServletRequest request = servletRequest.getRequest();
        PrivacyKind kind = privacyKind(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (kind == null || authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        try {
            var context = contextFactory.create(authentication, request);
            switch (kind) {
                case CREATE -> recorder.privacyCreate(context, false, List.of());
                case READ -> recorder.privacyRead(context, false, List.of());
                case UPDATE -> recorder.privacyUpdate(context, false, List.of());
            }
            return null;
        } catch (IdentityException exception) {
            String requestId = requestId(request);
            return ResponseEntity.status(exception.code().status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(exception.toProblemDetail(requestId));
        } catch (RuntimeException exception) {
            IdentityException unavailable = new IdentityException(
                    IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE,
                    exception);
            String requestId = requestId(request);
            return ResponseEntity.status(unavailable.code().status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(unavailable.toProblemDetail(requestId));
        }
    }

    private static PrivacyKind privacyKind(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/v1/auth/me/password".equals(path)) {
            return PrivacyKind.UPDATE;
        }
        if ("/api/v1/employees".equals(path)) {
            return "GET".equals(request.getMethod()) ? PrivacyKind.READ : PrivacyKind.CREATE;
        }
        return path.startsWith("/api/v1/employees/") ? PrivacyKind.UPDATE : null;
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        return value instanceof String text && !text.isBlank() ? text : "req-unavailable";
    }

    private enum PrivacyKind {
        CREATE,
        READ,
        UPDATE
    }
}
