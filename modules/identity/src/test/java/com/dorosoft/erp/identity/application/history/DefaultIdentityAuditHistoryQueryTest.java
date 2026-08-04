package com.dorosoft.erp.identity.application.history;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditQueryResult;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessResult;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.identity.application.error.IdentityException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultIdentityAuditHistoryQueryTest {
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private final FakeSecurityQuery security = new FakeSecurityQuery();
    private final FakeAuditQuery audit = new FakeAuditQuery();
    private final CapturingPrivacyLogger privacy = new CapturingPrivacyLogger();
    private final HmacIdentityAuditCursorCodec cursorCodec = cursorCodec();
    private final DefaultIdentityAuditHistoryQuery query = new DefaultIdentityAuditHistoryQuery(
            security, audit, privacy, cursorCodec, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void mergesSameTimestampWithSecuritySourceFirstAndDeduplicatesPrivacySubjects() {
        Instant occurredAt = NOW.minusSeconds(10);
        security.events.add(securityEvent(
                "00000000-0000-0000-0000-000000000001", occurredAt, ACCOUNT));
        audit.events.add(auditEvent(
                "ffffffff-ffff-ffff-ffff-ffffffffffff", occurredAt, ACCOUNT, ACTOR));

        IdentityAuditQueryResult result = query.query(filter(20, null), accessContext());

        assertEquals(List.of(
                        IdentityAuditSourceType.IDENTITY_SECURITY_EVENT,
                        IdentityAuditSourceType.AUDIT_RECORD),
                result.items().stream().map(IdentityAuditEventItem::sourceType).toList());
        assertNull(result.items().getFirst().actor());
        assertNull(result.items().getFirst().reason());
        assertTrue(result.items().getFirst().changedFields().isEmpty());
        assertEquals("SUCCESS", result.items().get(1).outcome());
        assertEquals(2, privacy.command.subjects().size());
        assertEquals(List.of(ACCOUNT, ACTOR), privacy.command.subjects().stream()
                .map(subject -> subject.subjectId()).sorted(Comparator.comparing(UUID::toString)).toList());
    }

    @Test
    void cursorProducesStablePagesWithoutDuplicateOrMissingItems() {
        Instant firstTime = NOW.minusSeconds(10);
        Instant secondTime = NOW.minusSeconds(20);
        security.events.add(securityEvent(
                "00000000-0000-0000-0000-000000000004", firstTime, ACCOUNT));
        audit.events.add(auditEvent(
                "00000000-0000-0000-0000-000000000003", firstTime, ACCOUNT, ACTOR));
        security.events.add(securityEvent(
                "00000000-0000-0000-0000-000000000002", secondTime, ACCOUNT));
        audit.events.add(auditEvent(
                "00000000-0000-0000-0000-000000000001", secondTime.minusSeconds(1), ACCOUNT, ACTOR));

        IdentityAuditQueryResult first = query.query(filter(2, null), accessContext());
        IdentityAuditQueryResult second = query.query(filter(2, first.nextCursor()), accessContext());

        List<UUID> all = new ArrayList<>();
        first.items().forEach(item -> all.add(item.eventId()));
        second.items().forEach(item -> all.add(item.eventId()));
        assertEquals(4, all.size());
        assertEquals(4, all.stream().distinct().count());
        assertNull(second.nextCursor());
    }

    @Test
    void actorFilterExcludesSecuritySourceAndPassesFilterToAuditPublicApi() {
        audit.events.add(auditEvent(
                "00000000-0000-0000-0000-000000000001", NOW.minusSeconds(1), ACCOUNT, ACTOR));
        IdentityAuditFilter filter = new IdentityAuditFilter(
                "ACCOUNT_LOGIN_UNLOCKED", ACTOR, ACCOUNT, NOW.minusSeconds(60), NOW, null, 20);

        IdentityAuditQueryResult result = query.query(filter, accessContext());

        assertEquals(0, security.calls);
        assertEquals(1, result.items().size());
        assertEquals(ACTOR, audit.lastFilter.actorId());
        assertEquals(ACCOUNT, audit.lastFilter.targetId());
        assertEquals("ACCOUNT_LOGIN_UNLOCKED", audit.lastFilter.action());
    }

    @Test
    void validatesPeriodSizeAndEventType() {
        assertThrows(IdentityException.class,
                () -> query.query(filter(101, null), accessContext()));
        assertThrows(IdentityException.class,
                () -> query.query(new IdentityAuditFilter(
                        null, null, null, NOW.minus(Duration.ofDays(367)), NOW, null, 20), accessContext()));
        assertThrows(IdentityException.class,
                () -> query.query(new IdentityAuditFilter(
                        "NOT_REGISTERED", null, null, null, null, null, 20), accessContext()));
    }

    @Test
    void privacyAppendFailurePreventsAResult() {
        security.events.add(securityEvent(
                "00000000-0000-0000-0000-000000000001", NOW.minusSeconds(1), ACCOUNT));
        privacy.fail = true;

        IdentityException exception = assertThrows(IdentityException.class,
                () -> query.query(filter(20, null), accessContext()));
        assertEquals(com.dorosoft.erp.identity.application.error.IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE,
                exception.code());
    }

    private IdentityAuditFilter filter(int size, String cursor) {
        return new IdentityAuditFilter(null, null, null, NOW.minus(Duration.ofDays(30)), NOW, cursor, size);
    }

    private IdentityAuditAccessContext accessContext() {
        return new IdentityAuditAccessContext(
                "tenant-a", ACTOR, "ADMIN", "req-history", "AQIDBA", "v1", NOW);
    }

    private IdentitySecurityEventQueryPort.SecurityEventView securityEvent(
            String id, Instant occurredAt, UUID accountId) {
        return new IdentitySecurityEventQueryPort.SecurityEventView(
                UUID.fromString(id), "LOGIN_FAILED", "FAILURE", accountId,
                "CREDENTIAL_MISMATCH", occurredAt, "req-security");
    }

    private AuditRecordProjection auditEvent(String id, Instant occurredAt, UUID accountId, UUID actorId) {
        return new AuditRecordProjection(
                UUID.fromString(id), "IDENTITY", "ACCOUNT_LOGIN_UNLOCKED", "ADMIN", actorId,
                "ADMIN", "관리자", null, "본인 확인", AuditTargetType.ACCOUNT, accountId,
                List.of("lockStatus", "version"), occurredAt, "req-audit", java.util.Map.of(),
                java.util.Map.of(), 1);
    }

    private HmacIdentityAuditCursorCodec cursorCodec() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 5);
        return new HmacIdentityAuditCursorCodec(
                key, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    private static final class FakeSecurityQuery implements IdentitySecurityEventQueryPort {
        private final List<SecurityEventView> events = new ArrayList<>();
        private int calls;

        @Override
        public List<SecurityEventView> query(String eventType, UUID targetAccountId, Instant from, Instant to,
                                             IdentityAuditSortKey boundary, int limit) {
            calls++;
            return events.stream()
                    .filter(event -> eventType == null || event.eventType().equals(eventType))
                    .filter(event -> targetAccountId == null || targetAccountId.equals(event.targetAccountId()))
                    .filter(event -> !event.occurredAt().isBefore(from) && event.occurredAt().isBefore(to))
                    .map(SecurityEventView::toItem)
                    .filter(item -> item.sortKey().isAfter(boundary))
                    .sorted(Comparator.comparing(IdentityAuditEventItem::sortKey, IdentityAuditSortKey.ORDER))
                    .limit(limit)
                    .map(item -> events.stream().filter(event -> event.eventId().equals(item.eventId())).findFirst().orElseThrow())
                    .toList();
        }
    }

    private static final class FakeAuditQuery implements AuditQuery {
        private final List<AuditRecordProjection> events = new ArrayList<>();
        private AuditQueryFilter lastFilter;

        @Override
        public AuditQueryResult query(String tenantId, AuditQueryFilter filter) {
            lastFilter = filter;
            List<AuditRecordProjection> filtered = events.stream()
                    .filter(event -> filter.action() == null || filter.action().equals(event.action()))
                    .filter(event -> filter.actorId() == null || filter.actorId().equals(event.actorId()))
                    .filter(event -> filter.targetId() == null || filter.targetId().equals(event.primaryTargetId()))
                    .toList();
            return new AuditQueryResult(filtered, null);
        }

        @Override
        public Optional<AuditRecordProjection> findById(String tenantId, UUID operationId) {
            return Optional.empty();
        }
    }

    private static final class CapturingPrivacyLogger implements PrivacyAccessLogger {
        private PrivacyAccessCommand command;
        private boolean fail;

        @Override
        public PrivacyAccessResult append(PrivacyAccessCommand command, PrivacyAccessContext context) {
            if (fail) {
                throw new AuditContractException(
                        AuditErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, "Privacy append failed");
            }
            this.command = command;
            return PrivacyAccessResult.success(UUID.randomUUID(), context.accessedAt());
        }
    }
}
