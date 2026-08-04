package com.dorosoft.erp.table.application;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditPrimaryTarget;
import com.dorosoft.erp.audit.domain.AuditRelatedTarget;
import com.dorosoft.erp.audit.domain.AuditRelationType;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.table.application.TableUsageSessionService.StartSessionContext;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardBlocker;
import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableFailureAuditService {

    private final AuditWriter auditWriter;
    private final Clock clock;

    public TableFailureAuditService(AuditWriter auditWriter, Clock clock) {
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public void recordSessionCloseBlocked(TableSessionCloseBlockedException exception) {
        TableUsageSessionSnapshot session = exception.session();
        StartSessionContext context = exception.context();
        String reasonCode = firstReasonCode(exception.blockers());
        AuditRecordCommand command =
                AuditRecordCommand.builder()
                        .domain(AuditDomain.ORDER)
                        .action(AuditAction.TABLE_SESSION_CLOSE_BLOCKED)
                        .operationId(UUID.randomUUID())
                        .eventSequence(0)
                        .primaryTarget(new AuditPrimaryTarget(AuditTargetType.TABLE_SESSION, session.sessionId()))
                        .relatedTargets(
                                List.of(
                                        new AuditRelatedTarget(
                                                AuditRelationType.TABLE,
                                                AuditTargetType.TABLE,
                                                session.tableId())))
                        .beforeValue(sessionAuditValue(session))
                        .afterValue(sessionAuditValue(session))
                        .reasonCode(reasonCode)
                        .reason("Table session close blocked.")
                        .valueSchemaVersion(1)
                        .build();
        auditWriter.record(
                command,
                AuditContext.identityUser(
                        context.tenantId(),
                        context.actorId(),
                        context.actorRoleCode(),
                        context.actorDisplayName(),
                        context.requestId(),
                        clock.instant()));
    }

    private static String firstReasonCode(List<CloseGuardBlocker> blockers) {
        if (blockers == null || blockers.isEmpty()) {
            return "TABLE_SESSION_CLOSE_BLOCKED";
        }
        String normalized =
                TableOperationObservability.lowCardinality(blockers.getFirst().code(), "TABLE_SESSION_CLOSE_BLOCKED")
                        .toUpperCase(java.util.Locale.ROOT)
                        .replaceAll("[^A-Z0-9_]", "_");
        return normalized.isBlank() ? "TABLE_SESSION_CLOSE_BLOCKED" : normalized;
    }

    private static Map<String, Object> sessionAuditValue(TableUsageSessionSnapshot session) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tableId", session.tableId().toString());
        value.put("status", session.status());
        value.put("openedAt", session.openedAt().toString());
        value.put("closedAt", session.closedAt() == null ? null : session.closedAt().toString());
        value.put("version", session.version());
        return value;
    }
}
