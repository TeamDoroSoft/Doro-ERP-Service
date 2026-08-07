package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee creation and Role/status management, including the OWNER/MANAGER authorization matrix, the
 * self-account-change block, and last-active-OWNER protection (ADR-02-010). {@link ActorContext} stands in
 * for the authenticated Session until feature 02 step 2's login is complete; a future Controller will build
 * it from the Session instead of a caller-supplied value.
 */
@Service
public class EmployeeManagementService {

    private final EmployeeAccountRepository employeeAccountRepository;
    private final Clock clock;

    public EmployeeManagementService(EmployeeAccountRepository employeeAccountRepository, Clock clock) {
        this.employeeAccountRepository = employeeAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public EmployeeAccount createEmployee(
            ActorContext actor, LoginId loginId, String temporaryPasswordHash, Role targetRole) {
        requireCanManage(actor, targetRole);
        Instant now = clock.instant();
        EmployeeAccount created = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), actor.tenantId(), actor.storeId(), loginId, temporaryPasswordHash, targetRole, now);
        return employeeAccountRepository.save(created);
    }

    @Transactional
    public EmployeeAccount changeRole(ActorContext actor, UUID targetEmployeeId, Role newRole) {
        EmployeeAccount target = requireManageableTarget(actor, targetEmployeeId);
        requireCanManage(actor, target.role());
        requireCanManage(actor, newRole);
        if (target.role() == Role.OWNER && newRole != Role.OWNER) {
            requireNotLastActiveOwner(actor.tenantId(), target);
        }
        return employeeAccountRepository.save(target.changeRole(newRole, clock.instant()));
    }

    @Transactional
    public EmployeeAccount changeStatus(ActorContext actor, UUID targetEmployeeId, EmployeeStatus newStatus) {
        EmployeeAccount target = requireManageableTarget(actor, targetEmployeeId);
        requireCanManage(actor, target.role());
        if (target.role() == Role.OWNER
                && target.status() == EmployeeStatus.ACTIVE
                && newStatus == EmployeeStatus.INACTIVE) {
            requireNotLastActiveOwner(actor.tenantId(), target);
        }
        return employeeAccountRepository.save(target.changeStatus(newStatus, clock.instant()));
    }

    private EmployeeAccount requireManageableTarget(ActorContext actor, UUID targetEmployeeId) {
        EmployeeAccount target = employeeAccountRepository.findById(targetEmployeeId)
                .filter(candidate -> candidate.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));
        if (target.id().equals(actor.employeeId())) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.SELF_ACCOUNT_ADMIN_CHANGE_NOT_ALLOWED,
                    "자기 계정은 관리자 API로 변경할 수 없습니다.");
        }
        return target;
    }

    /**
     * OWNER may manage any role. MANAGER may only manage the {@code STAFF} role — both as the role a target
     * currently holds and as the role being assigned. STAFF may not manage anyone.
     */
    private void requireCanManage(ActorContext actor, Role affectedRole) {
        boolean allowed = switch (actor.role()) {
            case OWNER -> true;
            case MANAGER -> affectedRole == Role.STAFF;
            case STAFF -> false;
        };
        if (!allowed) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN, "직원 관리 권한이 없습니다.");
        }
    }

    private void requireNotLastActiveOwner(UUID tenantId, EmployeeAccount target) {
        List<EmployeeAccount> activeOwners = employeeAccountRepository.findActiveOwnersForUpdate(tenantId);
        boolean isLastActiveOwner = activeOwners.size() <= 1
                && activeOwners.stream().anyMatch(owner -> owner.id().equals(target.id()));
        if (isLastActiveOwner) {
            throw new ProblemAwareException(
                    EmployeeManagementProblemCode.LAST_ACTIVE_OWNER_REQUIRED, "마지막 활성 OWNER는 변경할 수 없습니다.");
        }
    }
}
