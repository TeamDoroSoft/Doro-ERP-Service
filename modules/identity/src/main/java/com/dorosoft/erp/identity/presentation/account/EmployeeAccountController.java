package com.dorosoft.erp.identity.presentation.account;

import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.AccountStateResult;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.ChangeOwnPasswordCommand;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.ChangeStatusCommand;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.CreateEmployeeCommand;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.CreateEmployeeResult;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.EmployeeFilter;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.EmployeePage;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.ResetPasswordCommand;
import com.dorosoft.erp.identity.application.account.EmployeeAccountApplicationService.UnlockCommand;
import com.dorosoft.erp.identity.presentation.common.ApiEnvelope;
import com.dorosoft.erp.identity.presentation.common.IdentityRequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EmployeeAccountController {
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String CLEAR_SESSION_COOKIE =
            "__Host-ERPSESSION=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0";

    private final EmployeeAccountApplicationService service;
    private final IdentityOperationWebContextFactory contextFactory;

    public EmployeeAccountController(
            EmployeeAccountApplicationService service,
            IdentityOperationWebContextFactory contextFactory
    ) {
        this.service = service;
        this.contextFactory = contextFactory;
    }

    @PutMapping("/auth/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeOwnPassword(
            @Valid @RequestBody ChangeOwnPasswordRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        service.changeOwnPassword(
                new ChangeOwnPasswordCommand(body.currentPassword(), body.newPassword()),
                contextFactory.create(authentication, request));
        clearSession(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('identity.account.read')")
    public ApiEnvelope<EmployeePage> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lockStatus,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest request
    ) {
        EmployeePage page = service.list(
                new EmployeeFilter(status, lockStatus, roleCode, query, cursor, size),
                contextFactory.create(authentication, request));
        return ApiEnvelope.of(page, IdentityRequestId.from(request));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('identity.account.create')")
    public ResponseEntity<ApiEnvelope<CreateEmployeeResult>> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEmployeeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        CreateEmployeeResult result = service.create(
                new CreateEmployeeCommand(
                        body.loginId(), body.displayName(), body.initialPassword(), body.roleCode()),
                idempotencyKey, contextFactory.create(authentication, request));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + result.accountId()))
                .eTag(Long.toString(result.version()))
                .body(ApiEnvelope.of(result, IdentityRequestId.from(request)));
    }

    @PatchMapping("/employees/{accountId}/status")
    @PreAuthorize("hasAuthority('identity.account.status.update')")
    public ResponseEntity<ApiEnvelope<AccountStateResult>> changeStatus(
            @PathVariable UUID accountId,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ChangeStatusRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AccountStateResult result = service.changeStatus(
                accountId, ifMatch, new ChangeStatusCommand(body.status()),
                contextFactory.create(authentication, request));
        return ResponseEntity.ok().eTag(Long.toString(result.version()))
                .body(ApiEnvelope.of(result, IdentityRequestId.from(request)));
    }

    @PostMapping("/employees/{accountId}/unlock")
    @PreAuthorize("hasAuthority('identity.account.unlock')")
    public ResponseEntity<ApiEnvelope<AccountStateResult>> unlock(
            @PathVariable UUID accountId,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UnlockRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AccountStateResult result = service.unlock(
                accountId, ifMatch, new UnlockCommand(body.reason()),
                contextFactory.create(authentication, request));
        return ResponseEntity.ok().eTag(Long.toString(result.version()))
                .body(ApiEnvelope.of(result, IdentityRequestId.from(request)));
    }

    @PostMapping("/employees/{accountId}/password-reset")
    @PreAuthorize("hasAuthority('identity.credential.reset')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID accountId,
            @RequestHeader(name = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ResetPasswordRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        service.resetPassword(
                accountId, ifMatch, idempotencyKey,
                new ResetPasswordCommand(body.temporaryPassword()),
                contextFactory.create(authentication, request));
        if (accountId.toString().equals(authentication.getName())) {
            clearSession(response);
        }
        return ResponseEntity.noContent().build();
    }

    private static void clearSession(HttpServletResponse response) {
        response.addHeader("Set-Cookie", CLEAR_SESSION_COOKIE);
    }

    public record ChangeOwnPasswordRequest(
            @NotNull String currentPassword,
            @NotNull String newPassword
    ) {
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 50) String loginId,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull String initialPassword,
            @NotBlank @Size(max = 40) String roleCode
    ) {
    }

    public record ChangeStatusRequest(@NotBlank String status) {
    }

    public record UnlockRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ResetPasswordRequest(@NotNull String temporaryPassword) {
    }
}
