package com.dorosoft.erp.identity.presentation.authorization;

import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.EmployeeRoleResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.PermissionResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.ReplaceEmployeeRoleCommand;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.ReplaceRolePermissionsCommand;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.RolePermissionsResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.RoleResult;
import com.dorosoft.erp.identity.presentation.account.IdentityOperationWebContextFactory;
import com.dorosoft.erp.identity.presentation.common.ApiEnvelope;
import com.dorosoft.erp.identity.presentation.common.IdentityRequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {
    private static final String CLEAR_SESSION_COOKIE =
            "__Host-ERPSESSION=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0";

    private final AuthorizationApplicationService service;
    private final IdentityOperationWebContextFactory contextFactory;

    public AuthorizationController(
            AuthorizationApplicationService service,
            IdentityOperationWebContextFactory contextFactory
    ) {
        this.service = service;
        this.contextFactory = contextFactory;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity.role.read')")
    public ApiEnvelope<List<RoleResult>> roles(HttpServletRequest request) {
        return ApiEnvelope.of(service.listRoles(), IdentityRequestId.from(request));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('identity.role.read')")
    public ApiEnvelope<List<PermissionResult>> permissions(HttpServletRequest request) {
        return ApiEnvelope.of(service.listPermissions(), IdentityRequestId.from(request));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('identity.role.update')")
    public ResponseEntity<ApiEnvelope<RolePermissionsResult>> replaceRolePermissions(
            @PathVariable String roleCode,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        RolePermissionsResult result = service.replaceRolePermissions(
                roleCode, ifMatch, new ReplaceRolePermissionsCommand(body.permissionCodes()),
                contextFactory.create(authentication, request));
        if (result.reauthenticationRequired()) {
            clearSession(response);
        }
        return ResponseEntity.ok().eTag(Long.toString(result.version()))
                .body(ApiEnvelope.of(result, IdentityRequestId.from(request)));
    }

    @PutMapping("/employees/{accountId}/role")
    @PreAuthorize("hasAuthority('identity.role.assign')")
    public ResponseEntity<ApiEnvelope<EmployeeRoleResult>> replaceEmployeeRole(
            @PathVariable UUID accountId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ReplaceEmployeeRoleRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        EmployeeRoleResult result = service.replaceEmployeeRole(
                accountId, ifMatch, new ReplaceEmployeeRoleCommand(body.roleCode()),
                contextFactory.create(authentication, request));
        if (result.reauthenticationRequired()) {
            clearSession(response);
        }
        return ResponseEntity.ok().eTag(Long.toString(result.version()))
                .body(ApiEnvelope.of(result, IdentityRequestId.from(request)));
    }

    private static void clearSession(HttpServletResponse response) {
        response.addHeader("Set-Cookie", CLEAR_SESSION_COOKIE);
    }

    public record ReplaceRolePermissionsRequest(
            @NotNull @Size(max = 63) List<@NotBlank String> permissionCodes) {
    }

    public record ReplaceEmployeeRoleRequest(@NotBlank @Size(max = 40) String roleCode) {
    }
}
