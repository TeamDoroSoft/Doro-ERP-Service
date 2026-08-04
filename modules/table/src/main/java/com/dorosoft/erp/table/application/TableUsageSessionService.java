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
import com.dorosoft.erp.table.application.dto.TableUsageSessionResponse;
import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository;
import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableEntity;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableUsageSessionService {

    private final StoreTableJpaRepository tableRepository;
    private final TableUsageSessionCommandRepository sessionRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final String tenantId;

    public TableUsageSessionService(
            StoreTableJpaRepository tableRepository,
            TableUsageSessionCommandRepository sessionRepository,
            AuditWriter auditWriter,
            Clock clock,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
        this.tenantId = tenantId;
    }

    @Transactional
    public TableUsageSessionResponse start(UUID tableId, StartSessionContext context) {
        if (!tenantId.equals(context.tenantId())) {
            throw tableNotFound();
        }
        StoreTableEntity table =
                tableRepository.findByIdForUpdate(tableId).orElseThrow(TableUsageSessionService::tableNotFound);
        if (!table.isActive()) {
            throw new TableManagementException(
                    HttpStatus.CONFLICT,
                    TableErrorCode.TABLE_SESSION_NOT_AVAILABLE,
                    "Table session can not be opened.");
        }
        if (sessionRepository.existsOpenSession(table.getTableId())) {
            throw alreadyOpen();
        }

        TableUsageSessionSnapshot session = openSession(table.getTableId(), context.actorId(), clock.instant());
        recordAudit(session, context);
        return toResponse(session);
    }

    private TableUsageSessionSnapshot openSession(UUID tableId, UUID openedBy, Instant openedAt) {
        try {
            return sessionRepository.openSession(UUID.randomUUID(), tableId, openedBy, openedAt);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyOpen();
        }
    }

    private void recordAudit(TableUsageSessionSnapshot session, StartSessionContext context) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("tableId", session.tableId().toString());
        afterValue.put("status", session.status());
        afterValue.put("openedAt", session.openedAt().toString());
        afterValue.put("closedAt", null);
        afterValue.put("version", session.version());

        AuditRecordCommand command =
                AuditRecordCommand.builder()
                        .domain(AuditDomain.ORDER)
                        .action(AuditAction.TABLE_SESSION_OPENED)
                        .operationId(session.sessionId())
                        .eventSequence(0)
                        .primaryTarget(new AuditPrimaryTarget(AuditTargetType.TABLE_SESSION, session.sessionId()))
                        .relatedTargets(
                                List.of(
                                        new AuditRelatedTarget(
                                                AuditRelationType.TABLE,
                                                AuditTargetType.TABLE,
                                                session.tableId())))
                        .beforeValue(Map.of())
                        .afterValue(afterValue)
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
                        session.openedAt()));
    }

    private static TableUsageSessionResponse toResponse(TableUsageSessionSnapshot session) {
        return new TableUsageSessionResponse(
                session.sessionId(), session.tableId(), session.status(), session.openedAt());
    }

    private static TableManagementException tableNotFound() {
        return new TableManagementException(
                HttpStatus.NOT_FOUND,
                TableErrorCode.TABLE_NOT_FOUND,
                "Table not found.");
    }

    private static TableManagementException alreadyOpen() {
        return new TableManagementException(
                HttpStatus.CONFLICT,
                TableErrorCode.TABLE_SESSION_ALREADY_OPEN,
                "Table already has an open session.");
    }

    public record StartSessionContext(
            UUID actorId,
            String actorRoleCode,
            String actorDisplayName,
            String tenantId,
            String requestId) {}
}
