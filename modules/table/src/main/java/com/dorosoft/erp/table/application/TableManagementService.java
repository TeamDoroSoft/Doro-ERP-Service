package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.dto.TableResponse;
import com.dorosoft.erp.table.application.port.TableUsageStatusReader;
import com.dorosoft.erp.table.domain.TableUsageStatus;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableEntity;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableManagementService {

    private final StoreTableJpaRepository tableRepository;
    private final TableUsageStatusReader usageStatusReader;

    public TableManagementService(
            StoreTableJpaRepository tableRepository, TableUsageStatusReader usageStatusReader) {
        this.tableRepository = tableRepository;
        this.usageStatusReader = usageStatusReader;
    }

    @Transactional
    public TableResponse create(CreateTableCommand command) {
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
            return toResponse(tableRepository.saveAndFlush(entity), TableUsageStatus.VACANT);
        } catch (DataIntegrityViolationException e) {
            throw duplicatedNumber();
        }
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTables() {
        List<StoreTableEntity> tables = tableRepository.findAllByOrderByNormalizedNumberAsc();
        var statuses = usageStatusReader.getUsageStatuses(tables.stream().map(StoreTableEntity::getTableId).toList());
        return tables.stream()
                .map(table -> toResponse(table, statuses.getOrDefault(table.getTableId(), TableUsageStatus.VACANT)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID tableId) {
        StoreTableEntity table = findExisting(tableId);
        return toResponse(table, usageStatusReader.getUsageStatus(tableId));
    }

    @Transactional
    public TableResponse update(UUID tableId, long expectedVersion, UpdateTableCommand command) {
        StoreTableEntity table = findExisting(tableId);
        verifyVersion(table, expectedVersion);

        TableDetails details = validate(command.tableNumber(), command.displayName(), command.seatCapacity());
        ensureNumberAvailable(details.normalizedNumber(), tableId);

        table.applyDetails(
                details.tableNumber(),
                details.normalizedNumber(),
                details.displayName(),
                details.seatCapacity());
        try {
            StoreTableEntity saved = tableRepository.saveAndFlush(table);
            return toResponse(saved, usageStatusReader.getUsageStatus(tableId));
        } catch (DataIntegrityViolationException e) {
            throw duplicatedNumber();
        }
    }

    @Transactional
    public TableResponse updateActivation(UUID tableId, long expectedVersion, boolean active) {
        StoreTableEntity table = findExisting(tableId);
        verifyVersion(table, expectedVersion);

        if (table.isActive() != active) {
            table.applyActive(active);
            table = tableRepository.saveAndFlush(table);
        }

        return toResponse(table, usageStatusReader.getUsageStatus(tableId));
    }

    private StoreTableEntity findExisting(UUID tableId) {
        return tableRepository
                .findById(tableId)
                .orElseThrow(
                        () ->
                                new TableManagementException(
                                        HttpStatus.NOT_FOUND,
                                        TableErrorCode.TABLE_NOT_FOUND,
                                        "Table not found. tableId=" + tableId));
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

    private static TableManagementException duplicatedNumber() {
        return new TableManagementException(
                HttpStatus.CONFLICT,
                TableErrorCode.TABLE_NUMBER_DUPLICATED,
                "Table number is already in use.");
    }

    public record CreateTableCommand(
            String tableNumber, String displayName, int seatCapacity, boolean active) {}

    public record UpdateTableCommand(String tableNumber, String displayName, int seatCapacity) {}

    private record TableDetails(
            String tableNumber, String normalizedNumber, String displayName, int seatCapacity) {}
}
