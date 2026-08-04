package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableQrCredentialService;
import com.dorosoft.erp.table.application.TableQrCredentialService.TableQrOperationContext;
import com.dorosoft.erp.table.application.dto.QrCredentialIssueResponse;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/tables/{tableId}/qr-credentials")
class TableQrCredentialController {

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";

    private final TableQrCredentialService service;
    private final TableIdempotencyService idempotencyService;
    private final String tenantId;

    TableQrCredentialController(
            TableQrCredentialService service,
            TableIdempotencyService idempotencyService,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.tenantId = tenantId;
    }

    @PostMapping
    ResponseEntity<Object> issue(
            @PathVariable("tableId") UUID tableId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        return execute(idempotencyKey, servletRequest, () -> service.issue(tableId, context(authentication, servletRequest)));
    }

    @PostMapping("/reissue")
    ResponseEntity<Object> reissue(
            @PathVariable("tableId") UUID tableId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        return execute(
                idempotencyKey, servletRequest, () -> service.reissue(tableId, context(authentication, servletRequest)));
    }

    private ResponseEntity<Object> execute(
            String idempotencyKey,
            HttpServletRequest servletRequest,
            java.util.function.Supplier<QrCredentialIssueResponse> operation) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                Map.of(),
                () -> ResponseEntity.status(HttpStatus.CREATED).body(operation.get()),
                response -> ((QrCredentialIssueResponse) response.getBody()).replayWithoutSecret());
    }

    private TableQrOperationContext context(Authentication authentication, HttpServletRequest request) {
        String actorName = authentication == null ? "anonymous" : authentication.getName();
        return new TableQrOperationContext(
                actorId(actorName),
                actorRole(authentication),
                actorName,
                tenantId,
                requestId(request));
    }

    private static UUID actorId(String actorName) {
        try {
            return UUID.fromString(actorName);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(("table-qr-actor:" + actorName).getBytes(StandardCharsets.UTF_8));
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
