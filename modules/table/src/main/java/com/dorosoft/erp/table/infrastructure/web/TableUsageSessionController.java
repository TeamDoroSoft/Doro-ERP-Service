package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableUsageSessionService;
import com.dorosoft.erp.table.application.TableUsageSessionService.StartSessionContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tables/{tableId}/sessions")
class TableUsageSessionController {

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";

    private final TableUsageSessionService service;
    private final TableIdempotencyService idempotencyService;
    private final String tenantId;

    TableUsageSessionController(
            TableUsageSessionService service,
            TableIdempotencyService idempotencyService,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.tenantId = tenantId;
    }

    @PostMapping
    ResponseEntity<Object> start(
            @PathVariable("tableId") UUID tableId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                Map.of(),
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(service.start(tableId, context(authentication, servletRequest))));
    }

    @PostMapping("/{sessionId}/close")
    ResponseEntity<Object> close(
            @PathVariable("tableId") UUID tableId,
            @PathVariable("sessionId") UUID sessionId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                Map.of(),
                () -> ResponseEntity.ok(service.close(tableId, sessionId, context(authentication, servletRequest))));
    }

    private StartSessionContext context(Authentication authentication, HttpServletRequest request) {
        if (authentication != null) {
            StartSessionContext principalContext = identityPrincipalContext(authentication.getPrincipal(), request);
            if (principalContext != null) {
                return principalContext;
            }
        }

        String actorName = authentication == null ? "anonymous" : authentication.getName();
        return new StartSessionContext(
                actorId(actorName),
                actorRole(authentication),
                actorName,
                tenantId,
                requestId(request));
    }

    private StartSessionContext identityPrincipalContext(Object principal, HttpServletRequest request) {
        if (principal == null
                || !"com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal"
                        .equals(principal.getClass().getName())) {
            return null;
        }
        try {
            UUID accountId = (UUID) invoke(principal, "accountId");
            return new StartSessionContext(
                    accountId,
                    (String) invoke(principal, "roleCode"),
                    accountId.toString(),
                    (String) invoke(principal, "tenantKey"),
                    requestId(request));
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static UUID actorId(String actorName) {
        try {
            return UUID.fromString(actorName);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(("table-session-actor:" + actorName).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String actorRole(Authentication authentication) {
        if (authentication == null) {
            return "UNKNOWN";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("EMPLOYEE");
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String text && !text.isBlank()) {
            return text;
        }
        return "req-" + UUID.randomUUID();
    }
}
