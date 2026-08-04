package com.dorosoft.erp.identity.application.history;

import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;
import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Application-level merge of the Identity security ledger and Feature 17 Audit query. */
public final class DefaultIdentityAuditHistoryQuery implements IdentityAuditHistoryQuery {
    private static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
    private static final Duration MAX_PERIOD = Duration.ofDays(366);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int AUDIT_FETCH_SIZE = 100;
    private static final Set<String> IDENTITY_AUDIT_ACTIONS = Set.of(
            "EMPLOYEE_ACCOUNT_CREATED",
            "EMPLOYEE_ACCOUNT_ACTIVATED",
            "EMPLOYEE_ACCOUNT_DEACTIVATED",
            "ACCOUNT_LOGIN_UNLOCKED",
            "EMPLOYEE_PERMISSIONS_CHANGED",
            "ROLE_PERMISSIONS_CHANGED"
    );

    private final IdentitySecurityEventQueryPort securityEventQuery;
    private final AuditQuery auditQuery;
    private final PrivacyAccessLogger privacyAccessLogger;
    private final IdentityAuditCursorCodec cursorCodec;
    private final Clock clock;

    public DefaultIdentityAuditHistoryQuery(
            IdentitySecurityEventQueryPort securityEventQuery,
            AuditQuery auditQuery,
            PrivacyAccessLogger privacyAccessLogger,
            IdentityAuditCursorCodec cursorCodec,
            Clock clock
    ) {
        this.securityEventQuery = Objects.requireNonNull(securityEventQuery);
        this.auditQuery = Objects.requireNonNull(auditQuery);
        this.privacyAccessLogger = Objects.requireNonNull(privacyAccessLogger);
        this.cursorCodec = Objects.requireNonNull(cursorCodec);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public IdentityAuditQueryResult query(IdentityAuditFilter requested, IdentityAuditAccessContext accessContext) {
        Objects.requireNonNull(accessContext, "accessContext");
        IdentityAuditFilter safeRequest = requested == null
                ? new IdentityAuditFilter(null, null, null, null, null, null, 0)
                : requested;
        NormalizedIdentityAuditFilter filter = normalize(safeRequest, safeRequest.cursor() == null);
        IdentityAuditSortKey boundary = null;
        if (safeRequest.cursor() != null) {
            IdentityAuditCursorCodec.DecodedCursor decoded = cursorCodec.decode(
                    accessContext.tenantId(), filter, safeRequest.cursor());
            filter = decoded.filter();
            boundary = decoded.lastKey();
        }

        List<IdentityAuditEventItem> candidates = new ArrayList<>();
        if (includeSecurity(filter)) {
            securityEventQuery.query(
                            filter.eventTypeSource() == NormalizedIdentityAuditFilter.EventTypeSource.SECURITY
                                    ? filter.eventType() : null,
                            filter.targetAccountId(), filter.from(), filter.to(), boundary, filter.size() + 1)
                    .stream()
                    .map(IdentitySecurityEventQueryPort.SecurityEventView::toItem)
                    .forEach(candidates::add);
        }
        if (includeAudit(filter)) {
            candidates.addAll(loadAuditEvents(accessContext.tenantId(), filter, boundary));
        }

        candidates.sort(Comparator.comparing(IdentityAuditEventItem::sortKey, IdentityAuditSortKey.ORDER));
        boolean hasNext = candidates.size() > filter.size();
        List<IdentityAuditEventItem> items = List.copyOf(
                candidates.subList(0, Math.min(filter.size(), candidates.size())));
        String nextCursor = hasNext && !items.isEmpty()
                ? cursorCodec.encode(accessContext.tenantId(), filter, items.getLast().sortKey())
                : null;

        appendPrivacyAccess(items, accessContext);
        return new IdentityAuditQueryResult(items, nextCursor);
    }

    private List<IdentityAuditEventItem> loadAuditEvents(
            String tenantId,
            NormalizedIdentityAuditFilter filter,
            IdentityAuditSortKey boundary
    ) {
        List<IdentityAuditEventItem> result = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String auditCursor = null;
        do {
            if (auditCursor != null && !seenCursors.add(auditCursor)) {
                throw new IllegalStateException("Feature 17 Audit cursor did not advance");
            }
            AuditQueryFilter auditFilter = AuditQueryFilter.identity(
                    filter.eventTypeSource() == NormalizedIdentityAuditFilter.EventTypeSource.AUDIT
                            ? filter.eventType() : null,
                    filter.actorAccountId(), filter.targetAccountId(), filter.from(), filter.to(),
                    AUDIT_FETCH_SIZE, auditCursor);
            var page = auditQuery.query(tenantId, auditFilter);
            page.items().stream()
                    .map(this::toAuditItem)
                    .filter(item -> item.sortKey().isAfter(boundary))
                    .limit(filter.size() + 1L - result.size())
                    .forEach(result::add);
            auditCursor = page.nextCursor();
        } while (result.size() <= filter.size() && auditCursor != null);
        return result;
    }

    private IdentityAuditEventItem toAuditItem(AuditRecordProjection projection) {
        if (!"IDENTITY".equals(projection.domain()) || !IDENTITY_AUDIT_ACTIONS.contains(projection.action())) {
            throw new IllegalStateException("Feature 17 returned a non-Identity audit projection");
        }
        IdentityAuditActor actor = projection.actorId() == null ? null : new IdentityAuditActor(
                projection.actorId(), projection.actorRoleSnapshot(), projection.actorDisplayNameSnapshot());
        UUID targetAccountId = "ROLE_PERMISSIONS_CHANGED".equals(projection.action())
                ? null : projection.primaryTargetId();
        return new IdentityAuditEventItem(
                projection.auditId(),
                IdentityAuditSourceType.AUDIT_RECORD,
                projection.action(),
                "SUCCESS",
                actor,
                targetAccountId,
                null,
                projection.reason(),
                projection.changedFields(),
                projection.occurredAt(),
                projection.requestId()
        );
    }

    private void appendPrivacyAccess(List<IdentityAuditEventItem> items, IdentityAuditAccessContext context) {
        TreeSet<UUID> subjectIds = new TreeSet<>(Comparator.comparing(UUID::toString));
        for (IdentityAuditEventItem item : items) {
            if (item.actor() != null && item.actor().accountId() != null) {
                subjectIds.add(item.actor().accountId());
            }
            if (item.targetAccountId() != null) {
                subjectIds.add(item.targetAccountId());
            }
        }
        List<PrivacyAccessSubject> subjects = subjectIds.stream()
                .map(id -> new PrivacyAccessSubject("EMPLOYEE", id))
                .toList();
        try {
            privacyAccessLogger.append(
                    PrivacyAccessCommand.identityAuditEventRead(subjects),
                    new PrivacyAccessContext(
                            context.tenantId(),
                            "ADMIN",
                            context.accessorAccountId(),
                            context.accessorRoleCode(),
                            context.requestId(),
                            context.clientAddressCiphertext(),
                            context.clientAddressKeyVersion(),
                            context.accessedAt()
                    )
            );
        } catch (AuditContractException exception) {
            if (exception.code() == AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
            }
            throw exception;
        }
    }

    private NormalizedIdentityAuditFilter normalize(IdentityAuditFilter requested, boolean applyDefaultPeriod) {
        int size = requested.size() == 0 ? DEFAULT_SIZE : requested.size();
        if (size < 1 || size > MAX_SIZE) {
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED);
        }
        Instant to = requested.to();
        Instant from = requested.from();
        if (applyDefaultPeriod) {
            to = to == null ? clock.instant() : to;
            from = from == null ? to.minus(DEFAULT_PERIOD) : from;
        }
        if (from != null && to != null
                && (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_PERIOD) > 0)) {
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED);
        }
        String eventType = requested.eventType();
        NormalizedIdentityAuditFilter.EventTypeSource source = classify(eventType);
        return new NormalizedIdentityAuditFilter(
                eventType, requested.actorAccountId(), requested.targetAccountId(), from, to, size, source);
    }

    private NormalizedIdentityAuditFilter.EventTypeSource classify(String eventType) {
        if (eventType == null) {
            return NormalizedIdentityAuditFilter.EventTypeSource.ALL;
        }
        if (eventType.isBlank()) {
            throw invalidEventType();
        }
        try {
            SecurityEventType.valueOf(eventType);
            return NormalizedIdentityAuditFilter.EventTypeSource.SECURITY;
        } catch (IllegalArgumentException ignored) {
            if (IDENTITY_AUDIT_ACTIONS.contains(eventType)) {
                return NormalizedIdentityAuditFilter.EventTypeSource.AUDIT;
            }
            throw invalidEventType();
        }
    }

    private IdentityException invalidEventType() {
        return new IdentityException(IdentityErrorCode.VALIDATION_FAILED);
    }

    private boolean includeSecurity(NormalizedIdentityAuditFilter filter) {
        return filter.actorAccountId() == null
                && filter.eventTypeSource() != NormalizedIdentityAuditFilter.EventTypeSource.AUDIT;
    }

    private boolean includeAudit(NormalizedIdentityAuditFilter filter) {
        return filter.eventTypeSource() != NormalizedIdentityAuditFilter.EventTypeSource.SECURITY;
    }
}
