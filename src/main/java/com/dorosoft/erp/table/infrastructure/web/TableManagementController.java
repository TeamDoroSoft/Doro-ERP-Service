package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableManagementService;
import com.dorosoft.erp.table.application.TableManagementService.CreateTableCommand;
import com.dorosoft.erp.table.application.TableManagementService.UpdateTableCommand;
import com.dorosoft.erp.table.application.dto.TableResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tables")
class TableManagementController {

    private final TableManagementService service;
    private final TableIdempotencyService idempotencyService;

    TableManagementController(TableManagementService service, TableIdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    ResponseEntity<Object> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTableRequest request,
            HttpServletRequest servletRequest) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                request,
                () ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                        service.create(
                                                new CreateTableCommand(
                                                        request.tableNumber(),
                                                        request.displayName(),
                                                        request.seatCapacity(),
                                                        request.activeOrDefault()))));
    }

    @GetMapping
    List<TableResponse> getTables() {
        return service.getTables();
    }

    @GetMapping("/{tableId}")
    TableResponse getTable(@PathVariable UUID tableId) {
        return service.getTable(tableId);
    }

    @PutMapping("/{tableId}")
    ResponseEntity<Object> update(
            @PathVariable UUID tableId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateTableRequest request,
            HttpServletRequest servletRequest) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                request,
                () ->
                        ResponseEntity.ok(
                                service.update(
                                        tableId,
                                        TablePreconditions.requiredVersion(ifMatch),
                                        new UpdateTableCommand(
                                                request.tableNumber(),
                                                request.displayName(),
                                                request.seatCapacity()))));
    }

    @PatchMapping("/{tableId}/activation")
    ResponseEntity<Object> updateActivation(
            @PathVariable UUID tableId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateActivationRequest request,
            HttpServletRequest servletRequest) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                request,
                () ->
                        ResponseEntity.ok(
                                service.updateActivation(
                                        tableId,
                                        TablePreconditions.requiredVersion(ifMatch),
                                        request.active())));
    }

    record CreateTableRequest(
            @NotBlank @Size(max = 20) String tableNumber,
            @NotBlank @Size(max = 60) String displayName,
            @Min(1) @Max(999) int seatCapacity,
            Boolean active) {

        boolean activeOrDefault() {
            return active == null || active;
        }
    }

    record UpdateTableRequest(
            @NotBlank @Size(max = 20) String tableNumber,
            @NotBlank @Size(max = 60) String displayName,
            @Min(1) @Max(999) int seatCapacity) {}

    record UpdateActivationRequest(@NotNull Boolean active) {}
}
