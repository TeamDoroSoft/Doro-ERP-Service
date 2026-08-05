package com.dorosoft.erp.table.application;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditPrimaryTarget;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.table.application.dto.TableResponse;
import com.dorosoft.erp.table.application.port.TableUsageStatusReader;
import com.dorosoft.erp.table.domain.TableUsageStatus;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableEntity;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableManagementService {

    private final StoreTableJpaRepository tableRepository;
    private final TableUsageStatusReader usageStatusReader;
    private final AuditWriter auditWriter;
    private final TableOperationObservability observability;
    private final Clock clock;
    private final String tenantId;

    public TableManagementService(
            StoreTableJpaRepository tableRepository,
            TableUsageStatusReader usageStatusReader,
            AuditWriter auditWriter,
            TableOperationObservability observability,
            Clock clock,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.tableRepository = tableRepository;
        this.usageStatusReader = usageStatusReader;
        this.auditWriter = auditWriter;
        this.observability = observability;
        this.clock = clock;
        this.tenantId = tenantId;
    }

    @Transactional
    public TableResponse create(CreateTableCommand command, TableOperationContext context) {
        try {
            verifyTenant(context);
            TableDetails details = validate(command.tableNumber(), command.displayName(), command.seatCapacity());
            ensureNumberAvailable(details.normalizedNumber(), null);

            StoreTableEntity entity =
                    StoreTableEntity.create(
                            UUID.randomUUID(),
                            details.tableNumber(),
                            details.normalizedNumber(),
                            details.displayName(),
                            details.seatCapacity(),
                            command.active());
            try {
                StoreTableEntity saved = tableRepository.saveAndFlush(entity);
                recordTableAudit(
                        AuditAction.TABLE_CREATED,
                        saved.getTableId(),
                        saved.getTableId(),
                        Map.of(),
                        tableAuditValue(saved),
                        context);
                observability.success(
                        "table.create",
                        context.tenantId(),
                        context.actorId(),
                        context.requestId(),
                        "TABLE",
                        saved.getTableId());
                return toResponse(saved, TableUsageStatus.VACANT);
            } catch (DataIntegrityViolationException e) {
                throw duplicatedNumber();
            }
        } catch (RuntimeException exception) {
            observability.failure(
                    "table.create",
                    TableOperationObservability.reason(exception),
                    context.tenantId(),
                    context.actorId(),
                    context.requestId(),
                    "TABLE",
                    null);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTables(TableOperationContext context) {
        if (!tenantMatches(context)) {
            return List.of();
        }
        List<StoreTableEntity> tables = tableRepository.findAllByOrderByNormalizedNumberAsc();
        var statuses = usageStatusReader.getUsageStatuses(tables.stream().map(StoreTableEntity::getTableId).toList());
        return tables.stream()
                .map(table -> toResponse(table, statuses.getOrDefault(table.getTableId(), TableUsageStatus.VACANT)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID tableId, TableOperationContext context) {
        verifyTenant(context);
        StoreTableEntity table = findExisting(tableId);
        return toResponse(table, usageStatusReader.getUsageStatus(tableId));
    }

    @Transactional
    public TableResponse update(
            UUID tableId,
            long expectedVersion,
            UpdateTableCommand command,
            TableOperationContext context) {
        try {
            verifyTenant(context);
            StoreTableEntity table = findExisting(tableId);
            verifyVersion(table, expectedVersion);
            Map<String, Object> beforeValue = tableAuditValue(table);

            TableDetails details = validate(command.tableNumber(), command.displayName(), command.seatCapacity());
            ensureNumberAvailable(details.normalizedNumber(), tableId);

            table.applyDetails(
                    details.tableNumber(),
                    details.normalizedNumber(),
                    details.displayName(),
                    details.seatCapacity());
            try {
                StoreTableEntity saved = tableRepository.saveAndFlush(table);
                recordTableAudit(
                        AuditAction.TABLE_UPDATED,
                        UUID.randomUUID(),
                        saved.getTableId(),
                        beforeValue,
                        tableAuditValue(saved),
                        context);
                observability.success(
                        "table.update",
                        context.tenantId(),
                        context.actorId(),
                        context.requestId(),
                        "TABLE",
                        saved.getTableId());
                return toResponse(saved, usageStatusReader.getUsageStatus(tableId));
            } catch (DataIntegrityViolationException e) {
                throw duplicatedNumber();
            }
        } catch (RuntimeException exception) {
            observability.failure(
                    "table.update",
                    TableOperationObservability.reason(exception),
                    context.tenantId(),
                    context.actorId(),
                    context.requestId(),
                    "TABLE",
                    tableId);
            throw exception;
        }
    }

    @Transactional
    public TableResponse updateActivation(
            UUID tableId,
            long expectedVersion,
            boolean active,
            TableOperationContext context) {
        try {
            verifyTenant(context);
            StoreTableEntity table = findExisting(tableId);
            verifyVersion(table, expectedVersion);
            Map<String, Object> beforeValue = tableActivationAuditValue(table);
            boolean changed = table.isActive() != active;

            if (changed) {
                table.applyActive(active);
                table = tableRepository.saveAndFlush(table);
                recordTableAudit(
                        AuditAction.TABLE_ACTIVATION_CHANGED,
                        UUID.randomUUID(),
                        table.getTableId(),
                        beforeValue,
                        tableActivationAuditValue(table),
                        context);
            }
            observability.success(
                    active ? "table.activate" : "table.deactivate",
                    context.tenantId(),
                    context.actorId(),
                    context.requestId(),
                    "TABLE",
                    table.getTableId());
            return toResponse(table, usageStatusReader.getUsageStatus(tableId));
        } catch (RuntimeException exception) {
            observability.failure(
                    active ? "table.activate" : "table.deactivate",
                    TableOperationObservability.reason(exception),
                    context.tenantId(),
                    context.actorId(),
                    context.requestId(),
                    "TABLE",
                    tableId);
            throw exception;
        }
    }

    private StoreTableEntity findExisting(UUID tableId) {
        return tableRepository
                .findById(tableId)
                .orElseThrow(TableManagementService::tableNotFound);
    }

    private boolean tenantMatches(TableOperationContext context) {
        return tenantId.equals(context.tenantId());
    }

    private void verifyTenant(TableOperationContext context) {
        if (!tenantMatches(context)) {
            throw tableNotFound();
        }
    }

    private void verifyVersion(StoreTableEntity table, long expectedVersion) {
        if (table.getVersion() != expectedVersion) {
            throw new TableManagementException(
                    HttpStatus.PRECONDITION_FAILED,
                    TableErrorCode.PRECONDITION_FAILED,
                    "Table version does not match If-Match.");
        }
    }

    private void ensureNumberAvailable(String normalizedNumber, UUID selfTableId) {
        Optional<StoreTableEntity> existing = tableRepository.findByNormalizedNumber(normalizedNumber);
        if (existing.isPresent() && !existing.orElseThrow().getTableId().equals(selfTableId)) {
            throw duplicatedNumber();
        }
    }

    private TableDetails validate(String tableNumber, String displayName, int seatCapacity) {
        String normalizedTableNumber = normalizeText(tableNumber);
        if (normalizedTableNumber.isBlank() || normalizedTableNumber.length() > 20) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_TABLE_NUMBER,
                    "tableNumber must be 1 to 20 characters.");
        }

        String normalizedDisplayName = normalizeText(displayName);
        if (normalizedDisplayName.isBlank() || normalizedDisplayName.length() > 60) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_DISPLAY_NAME,
                    "displayName must be 1 to 60 characters.");
        }

        if (seatCapacity < 1 || seatCapacity > 999) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_SEAT_CAPACITY,
                    "seatCapacity must be between 1 and 999.");
        }

        return new TableDetails(
                normalizedTableNumber,
                normalizeTableNumber(normalizedTableNumber),
                normalizedDisplayName,
                seatCapacity);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeTableNumber(String tableNumber) {
        return tableNumber.toUpperCase(Locale.ROOT);
    }

    private TableResponse toResponse(StoreTableEntity table, TableUsageStatus usageStatus) {
        return new TableResponse(
                table.getTableId(),
                table.getTableNumber(),
                table.getDisplayName(),
                table.getSeatCapacity(),
                table.isActive(),
                usageStatus,
                table.getVersion());
    }

    private void recordTableAudit(
            AuditAction action,
            UUID operationId,
            UUID tableId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            TableOperationContext context) {
        AuditRecordCommand command =
                AuditRecordCommand.builder()
                        .domain(AuditDomain.STORE)
                        .action(action)
                        .operationId(operationId)
                        .eventSequence(0)
                        .primaryTarget(new AuditPrimaryTarget(AuditTargetType.TABLE, tableId))
                        .beforeValue(beforeValue)
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
                        clock.instant()));
    }

    private static Map<String, Object> tableAuditValue(StoreTableEntity table) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tableNumber", table.getTableNumber());
        value.put("displayName", table.getDisplayName());
        value.put("seatCapacity", table.getSeatCapacity());
        value.put("active", table.isActive());
        value.put("version", table.getVersion());
        return value;
    }

    private static Map<String, Object> tableActivationAuditValue(StoreTableEntity table) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("active", table.isActive());
        value.put("version", table.getVersion());
        return value;
    }

    private static TableManagementException duplicatedNumber() {
        return new TableManagementException(
                HttpStatus.CONFLICT,
                TableErrorCode.TABLE_NUMBER_DUPLICATED,
                "Table number is already in use.");
    }

    private static TableManagementException tableNotFound() {
        return new TableManagementException(
                HttpStatus.NOT_FOUND,
                TableErrorCode.TABLE_NOT_FOUND,
                "Table not found.");
    }

    public record CreateTableCommand(
            String tableNumber, String displayName, int seatCapacity, boolean active) {}

    public record UpdateTableCommand(String tableNumber, String displayName, int seatCapacity) {}

    public record TableOperationContext(
            UUID actorId,
            String actorRoleCode,
            String actorDisplayName,
            String tenantId,
            String requestId) {}

    private record TableDetails(
            String tableNumber, String normalizedNumber, String displayName, int seatCapacity) {}
}
