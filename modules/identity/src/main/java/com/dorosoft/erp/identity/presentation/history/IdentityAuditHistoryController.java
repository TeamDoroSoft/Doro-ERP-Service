package com.dorosoft.erp.identity.presentation.history;

import com.dorosoft.erp.identity.application.history.IdentityAuditFilter;
import com.dorosoft.erp.identity.application.history.IdentityAuditHistoryQuery;
import com.dorosoft.erp.identity.presentation.common.ApiEnvelope;
import com.dorosoft.erp.identity.presentation.common.IdentityRequestId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP adapter definition. It is registered as a Spring bean only after the approved cursor and
 * Privacy address-key providers are wired; this prevents an insecure partial endpoint.
 */
@RequestMapping("/api/v1/identity/audit-events")
@RestController
public final class IdentityAuditHistoryController {
    private final IdentityAuditHistoryQuery historyQuery;
    private final IdentityAuditWebContextFactory accessContextFactory;

    public IdentityAuditHistoryController(
            IdentityAuditHistoryQuery historyQuery,
            IdentityAuditWebContextFactory accessContextFactory
    ) {
        this.historyQuery = historyQuery;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('identity.audit.read')")
    public ResponseEntity<ApiEnvelope<IdentityAuditHistoryResponse>> query(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actorAccountId,
            @RequestParam(required = false) UUID targetAccountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String requestId = IdentityRequestId.from(request);
        var accessContext = accessContextFactory.create(authentication, request);
        var result = historyQuery.query(
                new IdentityAuditFilter(eventType, actorAccountId, targetAccountId, from, to, cursor, size),
                accessContext);
        var response = new IdentityAuditHistoryResponse(
                result.items().stream().map(IdentityAuditHistoryResponse.Item::from).toList(),
                result.nextCursor());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiEnvelope.of(response, requestId));
    }
}
