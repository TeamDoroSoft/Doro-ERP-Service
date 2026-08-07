package com.dorosoft.erp.storeaccess.presentation.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.EmployeeIdentitySnapshot;
import com.dorosoft.erp.storeaccess.application.api.identity.EmployeeManagementService;
import com.dorosoft.erp.storeaccess.application.api.identity.EmployeeSummary;
import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import com.dorosoft.erp.storeaccess.application.port.identity.CurrentActorResolver;
import com.dorosoft.erp.storeaccess.application.port.identity.RecentReauthenticationChecker;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee create/query/Role/status/password-reset (ADR-02-009/010/014). Every mutation is genuine user
 * activity (ADR-02-003) so idle Session renewal applies; STAFF is forbidden from every method here, enforced
 * inside {@link EmployeeManagementService}.
 */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController {

    private final EmployeeManagementService employeeManagementService;
    private final CurrentActorResolver currentActorResolver;
    private final RecentReauthenticationChecker recentReauthenticationChecker;

    EmployeeController(
            EmployeeManagementService employeeManagementService,
            CurrentActorResolver currentActorResolver,
            RecentReauthenticationChecker recentReauthenticationChecker) {
        this.employeeManagementService = employeeManagementService;
        this.currentActorResolver = currentActorResolver;
        this.recentReauthenticationChecker = recentReauthenticationChecker;
    }

    @UserActivity
    @PostMapping
    ResponseEntity<EmployeeResponse> createEmployee(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody CreateEmployeeRequest request) {
        recentReauthenticationChecker.requireRecentReauthentication();
        EmployeeSummary created = employeeManagementService.createEmployeeIdempotently(
                currentActorResolver.currentActor(), idempotencyKey, request.loginId(), request.temporaryPassword(),
                request.role());
        return ResponseEntity.ok(toResponse(created));
    }

    @UserActivity
    @GetMapping
    ResponseEntity<List<EmployeeResponse>> listEmployees() {
        List<EmployeeResponse> employees = employeeManagementService.listEmployees(currentActorResolver.currentActor())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(employees);
    }

    @UserActivity
    @GetMapping("/{employeeId}")
    ResponseEntity<EmployeeResponse> getEmployee(@PathVariable UUID employeeId) {
        EmployeeSummary employee = employeeManagementService.getEmployee(currentActorResolver.currentActor(), employeeId);
        return ResponseEntity.ok(toResponse(employee));
    }

    @UserActivity
    @PatchMapping("/{employeeId}/role")
    ResponseEntity<EmployeeResponse> changeRole(
            @PathVariable UUID employeeId, @Valid @RequestBody ChangeRoleRequest request) {
        recentReauthenticationChecker.requireRecentReauthentication();
        EmployeeSummary updated = employeeManagementService.changeRole(
                currentActorResolver.currentActor(), employeeId, request.role());
        return ResponseEntity.ok(toResponse(updated));
    }

    @UserActivity
    @PatchMapping("/{employeeId}/status")
    ResponseEntity<EmployeeResponse> changeStatus(
            @PathVariable UUID employeeId, @Valid @RequestBody ChangeStatusRequest request) {
        recentReauthenticationChecker.requireRecentReauthentication();
        EmployeeSummary updated = employeeManagementService.changeStatus(
                currentActorResolver.currentActor(), employeeId, request.status());
        return ResponseEntity.ok(toResponse(updated));
    }

    @UserActivity
    @PostMapping("/{employeeId}/password-reset")
    ResponseEntity<EmployeeIdentityResponse> resetPassword(
            @PathVariable UUID employeeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ResetPasswordRequest request) {
        recentReauthenticationChecker.requireRecentReauthentication();
        EmployeeIdentitySnapshot result = employeeManagementService.resetPassword(
                currentActorResolver.currentActor(), idempotencyKey, employeeId, request.newTemporaryPassword());
        return ResponseEntity.ok(new EmployeeIdentityResponse(
                result.employeeId(), result.roleName(), result.statusName(), result.passwordChangeRequired()));
    }

    @UserActivity
    @PatchMapping("/me/password")
    ResponseEntity<EmployeeResponse> changeOwnPassword(@Valid @RequestBody ChangeOwnPasswordRequest request) {
        EmployeeSummary updated = employeeManagementService.changeOwnPasswordForController(
                currentActorResolver.currentActor(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(toResponse(updated));
    }

    private EmployeeResponse toResponse(EmployeeSummary summary) {
        return new EmployeeResponse(
                summary.id(), summary.loginId(), summary.role(), summary.status(),
                summary.passwordChangeRequired(), summary.createdAt());
    }
}
