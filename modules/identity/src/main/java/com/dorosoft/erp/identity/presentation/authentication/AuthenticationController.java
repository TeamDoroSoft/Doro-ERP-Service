package com.dorosoft.erp.identity.presentation.authentication;

import com.dorosoft.erp.identity.application.authentication.AuthenticatedActor;
import com.dorosoft.erp.identity.application.authentication.CurrentIdentitySessionService;
import com.dorosoft.erp.identity.application.authentication.LoginAuthenticationService;
import com.dorosoft.erp.identity.application.authentication.LoginCommand;
import com.dorosoft.erp.identity.application.authentication.AuthenticatedActorResolver;
import com.dorosoft.erp.identity.application.authentication.IdentityClientAddressResolver;
import com.dorosoft.erp.identity.application.authentication.IdentitySessionCookiePort;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.error.LoginRateLimitedException;
import com.dorosoft.erp.identity.presentation.common.ApiEnvelope;
import com.dorosoft.erp.identity.presentation.common.IdentityRequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final LoginAuthenticationService loginService;
    private final CurrentIdentitySessionService currentSessionService;
    private final IdentityClientAddressResolver clientIpResolver;
    private final CsrfTokenRepository csrfTokenRepository;
    private final IdentitySessionCookiePort sessionCookie;
    private final AuthenticatedActorResolver actorResolver;

    public AuthenticationController(
            LoginAuthenticationService loginService,
            CurrentIdentitySessionService currentSessionService,
            IdentityClientAddressResolver clientIpResolver,
            CsrfTokenRepository csrfTokenRepository,
            IdentitySessionCookiePort sessionCookie,
            AuthenticatedActorResolver actorResolver
    ) {
        this.loginService = loginService;
        this.currentSessionService = currentSessionService;
        this.clientIpResolver = clientIpResolver;
        this.csrfTokenRepository = csrfTokenRepository;
        this.sessionCookie = sessionCookie;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiEnvelope<LoginResponse>> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String requestId = IdentityRequestId.from(request);
        var result = loginService.login(new LoginCommand(
                body.loginId(), body.password(), clientIpResolver.resolve(request),
                clientIpResolver.resolveLiteral(request), requestId));
        sessionCookie.issue(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.of(LoginResponse.from(result), requestId));
    }

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            currentSessionService.logout(
                    sessionCookie.read(request), actorResolver.resolve(authentication), IdentityRequestId.from(request));
            return ResponseEntity.noContent().build();
        } finally {
            sessionCookie.clear(response);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiEnvelope<MeResponse>> me(
            Authentication authentication,
            HttpServletRequest request
    ) {
        AuthenticatedActor actor = requiredActor(authentication);
        String requestId = IdentityRequestId.from(request);
        var result = currentSessionService.me(actor, requestId, clientIpResolver.resolveLiteral(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.of(MeResponse.from(result), requestId));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiEnvelope<CsrfResponse>> csrf(
            Authentication authentication,
            HttpServletRequest request
    ) {
        requiredActor(authentication);
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED);
        }
        String requestId = IdentityRequestId.from(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.of(new CsrfResponse(token.getToken()), requestId));
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(
            LoginRateLimitedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(exception.toProblemDetail(IdentityRequestId.from(request)));
    }

    private AuthenticatedActor requiredActor(Authentication authentication) {
        AuthenticatedActor actor = actorResolver.resolve(authentication);
        if (actor == null) {
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED);
        }
        return actor;
    }

}
