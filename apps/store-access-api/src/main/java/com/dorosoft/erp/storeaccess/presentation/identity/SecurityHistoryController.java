package com.dorosoft.erp.storeaccess.presentation.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.SecurityHistoryEntry;
import com.dorosoft.erp.storeaccess.application.api.identity.SecurityHistoryPage;
import com.dorosoft.erp.storeaccess.application.api.identity.SecurityHistoryQueryService;
import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import com.dorosoft.erp.storeaccess.application.port.identity.CurrentActorResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local security history query (ADR-02-015): {@code GET /api/v1/security-history}. Cursor pagination uses
 * the last returned entry's {@code occurredAt}/{@code id} as the next request's {@code cursorOccurredAt}/
 * {@code cursorId}.
 */
@RestController
@RequestMapping("/api/v1/security-history")
class SecurityHistoryController {

    private final SecurityHistoryQueryService securityHistoryQueryService;
    private final CurrentActorResolver currentActorResolver;

    SecurityHistoryController(
            SecurityHistoryQueryService securityHistoryQueryService, CurrentActorResolver currentActorResolver) {
        this.securityHistoryQueryService = securityHistoryQueryService;
        this.currentActorResolver = currentActorResolver;
    }

    @UserActivity
    @GetMapping
    ResponseEntity<SecurityHistoryPageResponse> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorOccurredAt,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        SecurityHistoryPage page = securityHistoryQueryService.searchForController(
                currentActorResolver.currentActor(), from, to, eventType, targetType, targetId, result,
                cursorOccurredAt, cursorId, size);
        List<SecurityHistoryEntry> entries = page.items();
        List<SecurityHistoryEntryResponse> items = entries.stream().map(this::toResponse).toList();
        SecurityHistoryEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        return ResponseEntity.ok(new SecurityHistoryPageResponse(
                items, last == null ? null : last.occurredAt(), last == null ? null : last.id(), page.hasMore()));
    }

    private SecurityHistoryEntryResponse toResponse(SecurityHistoryEntry entry) {
        return new SecurityHistoryEntryResponse(
                entry.id(), entry.eventType(), entry.actorEmployeeId(), entry.targetType(), entry.targetId(),
                entry.result(), entry.reasonCode(), entry.previousValue(), entry.newValue(), entry.occurredAt());
    }
}
