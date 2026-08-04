package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.TableUsageSessionService.StartSessionContext;
import com.dorosoft.erp.table.application.dto.TableOrderPageResponse;
import com.dorosoft.erp.table.application.dto.TableOrderPageResponse.SessionSummary;
import com.dorosoft.erp.table.application.dto.TableSessionHistoryPageResponse;
import com.dorosoft.erp.table.application.port.TableSessionOrderReader;
import com.dorosoft.erp.table.application.port.TableSessionOrderReader.OrderQuery;
import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository;
import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository.SessionHistoryCursor;
import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository.SessionHistoryPage;
import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository.SessionHistoryQuery;
import com.dorosoft.erp.table.infrastructure.persistence.StoreTableJpaRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableOrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final StoreTableJpaRepository tableRepository;
    private final TableUsageSessionQueryRepository sessionRepository;
    private final TableSessionOrderReader orderReader;
    private final TableSessionHistoryCursorCodec cursorCodec;
    private final String tenantId;

    public TableOrderQueryService(
            StoreTableJpaRepository tableRepository,
            TableUsageSessionQueryRepository sessionRepository,
            TableSessionOrderReader orderReader,
            TableSessionHistoryCursorCodec cursorCodec,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.orderReader = orderReader;
        this.cursorCodec = cursorCodec;
        this.tenantId = tenantId;
    }

    @Transactional(readOnly = true)
    public TableOrderPageResponse currentOrders(
            UUID tableId,
            String status,
            String cursor,
            Integer size,
            StartSessionContext context) {
        verifyTenantAndTable(tableId, context);
        String normalizedStatus = normalizeStatus(status);
        int normalizedSize = normalizeSize(size);
        return sessionRepository
                .findOpenSession(tableId)
                .map(session -> ordersForSession(session, normalizedStatus, cursor, normalizedSize))
                .orElseGet(() -> new TableOrderPageResponse(null, List.of(), null));
    }

    @Transactional(readOnly = true)
    public TableSessionHistoryPageResponse pastSessions(
            UUID tableId,
            String from,
            String to,
            String cursor,
            Integer size,
            StartSessionContext context) {
        verifyTenantAndTable(tableId, context);
        Instant fromInstant = parseOptionalInstant(from);
        Instant toInstant = parseOptionalInstant(to);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_PERIOD,
                    "Period from must not be after to.");
        }
        int normalizedSize = normalizeSize(size);
        SessionHistoryCursor decodedCursor = cursorCodec.decode(cursor);
        SessionHistoryPage page =
                sessionRepository.findClosedSessions(
                        new SessionHistoryQuery(tableId, fromInstant, toInstant, decodedCursor, normalizedSize));
        String nextCursor =
                page.hasNext() && !page.items().isEmpty()
                        ? cursorCodec.encode(page.items().getLast().closedAt(), page.items().getLast().sessionId())
                        : null;
        return new TableSessionHistoryPageResponse(page.items().stream().map(TableOrderQueryService::summary).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public TableOrderPageResponse pastSessionOrders(
            UUID tableId,
            UUID sessionId,
            String status,
            String cursor,
            Integer size,
            StartSessionContext context) {
        verifyTenantAndTable(tableId, context);
        TableUsageSessionSnapshot session =
                sessionRepository
                        .findSession(tableId, sessionId)
                        .filter(found -> "CLOSED".equals(found.status()))
                        .orElseThrow(TableOrderQueryService::sessionNotFound);
        return ordersForSession(session, normalizeStatus(status), cursor, normalizeSize(size));
    }

    private TableOrderPageResponse ordersForSession(
            TableUsageSessionSnapshot session,
            String status,
            String cursor,
            int size) {
        var page =
                orderReader.findOrders(
                        new OrderQuery(session.tableId(), session.sessionId(), status, cursor, size));
        if (page == null) {
            page = TableSessionOrderReader.OrderPage.empty();
        }
        return new TableOrderPageResponse(summary(session), page.items(), page.nextCursor());
    }

    private void verifyTenantAndTable(UUID tableId, StartSessionContext context) {
        if (!tenantId.equals(context.tenantId())) {
            throw tableNotFound();
        }
        tableRepository.findById(tableId).orElseThrow(TableOrderQueryService::tableNotFound);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!orderReader.supportsStatus(normalized)) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.UNSUPPORTED_ORDER_STATUS,
                    "Order status is not supported.");
        }
        return normalized;
    }

    private static int normalizeSize(Integer size) {
        int normalized = size == null ? DEFAULT_PAGE_SIZE : size;
        if (normalized < 1 || normalized > MAX_PAGE_SIZE) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_PAGE_SIZE,
                    "Page size must be between 1 and 100.");
        }
        return normalized;
    }

    private static Instant parseOptionalInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_PERIOD,
                    "Period value is invalid.");
        }
    }

    private static SessionSummary summary(TableUsageSessionSnapshot session) {
        return new SessionSummary(
                session.sessionId(),
                session.tableId(),
                session.openedAt(),
                session.closedAt(),
                session.status());
    }

    private static TableManagementException tableNotFound() {
        return new TableManagementException(
                HttpStatus.NOT_FOUND,
                TableErrorCode.TABLE_NOT_FOUND,
                "Table not found.");
    }

    private static TableManagementException sessionNotFound() {
        return new TableManagementException(
                HttpStatus.NOT_FOUND,
                TableErrorCode.TABLE_SESSION_NOT_FOUND,
                "Table session not found.");
    }
}
