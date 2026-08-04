package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableManagementService;
import com.dorosoft.erp.table.application.TableManagementService.CreateTableCommand;
import com.dorosoft.erp.table.application.TableManagementService.TableOperationContext;
import com.dorosoft.erp.table.application.TableManagementService.UpdateTableCommand;
import com.dorosoft.erp.table.application.dto.TableResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";

    private final TableManagementService service;
    private final TableIdempotencyService idempotencyService;
    private final String tenantId;

    TableManagementController(
            TableManagementService service,
            TableIdempotencyService idempotencyService,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.tenantId = tenantId;
    }

    @PostMapping
    ResponseEntity<Object> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTableRequest request,
            HttpServletRequest servletRequest,
            Authentication authentication) {
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
                                                        request.activeOrDefault()),
                                                context(authentication, servletRequest))));
    }

    @GetMapping
    List<TableResponse> getTables() {
        return service.getTables();
    }

    @GetMapping("/{tableId}")
    TableResponse getTable(@PathVariable("tableId") UUID tableId) {
        return service.getTable(tableId);
    }

    @PutMapping("/{tableId}")
    ResponseEntity<Object> update(
            @PathVariable("tableId") UUID tableId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateTableRequest request,
            HttpServletRequest servletRequest,
            Authentication authentication) {
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
                                                request.seatCapacity()),
                                        context(authentication, servletRequest))));
    }

    @PatchMapping("/{tableId}/activation")
    ResponseEntity<Object> updateActivation(
            @PathVariable("tableId") UUID tableId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateActivationRequest request,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        return idempotencyService.execute(
                idempotencyKey,
                servletRequest,
                request,
                () ->
                        ResponseEntity.ok(
                                service.updateActivation(
                                        tableId,
                                        TablePreconditions.requiredVersion(ifMatch),
                                        request.active(),
                                        context(authentication, servletRequest))));
    }

    private TableOperationContext context(Authentication authentication, HttpServletRequest request) {
        if (authentication != null) {
            TableOperationContext principalContext = identityPrincipalContext(authentication.getPrincipal(), request);
            if (principalContext != null) {
                return principalContext;
            }
        }

        String actorName = authentication == null ? "anonymous" : authentication.getName();
        return new TableOperationContext(
                actorId(actorName),
                actorRole(authentication),
                actorName,
                tenantId,
                requestId(request));
    }

    private TableOperationContext identityPrincipalContext(Object principal, HttpServletRequest request) {
        if (principal == null
                || !"com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal"
                        .equals(principal.getClass().getName())) {
            return null;
        }
        try {
            UUID accountId = (UUID) invoke(principal, "accountId");
            return new TableOperationContext(
                    accountId,
                    (String) invoke(principal, "roleCode"),
                    accountId.toString(),
                    (String) invoke(principal, "tenantKey"),
                    requestId(request));
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static UUID actorId(String actorName) {
        try {
            return UUID.fromString(actorName);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(("table-management-actor:" + actorName).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String actorRole(Authentication authentication) {
        if (authentication == null) {
            return "UNKNOWN";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("EMPLOYEE");
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String text && !text.isBlank()) {
            return text;
        }
        return "req-" + UUID.randomUUID();
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
