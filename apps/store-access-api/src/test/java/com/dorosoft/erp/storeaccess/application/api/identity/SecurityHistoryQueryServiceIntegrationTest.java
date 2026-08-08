package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the Role-scoped visibility, 90-day range validation, and {@code occurredAt + id} cursor
 * pagination for local security history queries (ADR-02-015).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
                + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
})
@Testcontainers
class SecurityHistoryQueryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void identitySecrets(DynamicPropertyRegistry registry) {
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @Autowired
    private SecurityHistoryQueryService service;

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private EmployeeSecurityHistoryRepository employeeSecurityHistoryRepository;

    @Autowired
    private Clock clock;

    @Test
    void ownerSeesEveryTenantHistoryRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount owner = createAndSaveEmployee(tenantId, storeId, Role.OWNER);
        EmployeeAccount manager = createAndSaveEmployee(tenantId, storeId, Role.MANAGER);
        EmployeeAccount staff = createAndSaveEmployee(tenantId, storeId, Role.STAFF);
        saveHistory(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_CREATED, owner.id(), "EMPLOYEE", manager.id(), now());
        saveHistory(tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_CREATED, manager.id(), "EMPLOYEE", staff.id(), now());
        saveHistory(tenantId, storeId, SecurityHistoryEventType.KIOSK_DEVICE_REGISTERED, owner.id(), "KIOSK_DEVICE", UUID.randomUUID(), now());

        List<EmployeeSecurityHistory> results = service.search(actorFor(owner), wideRangeRequest(null));

        assertThat(results).hasSize(3);
    }

    @Test
    void managerSeesOnlySelfTargetedStaffTargetedAndKioskRecords() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount owner = createAndSaveEmployee(tenantId, storeId, Role.OWNER);
        EmployeeAccount manager = createAndSaveEmployee(tenantId, storeId, Role.MANAGER);
        EmployeeAccount otherManager = createAndSaveEmployee(tenantId, storeId, Role.MANAGER);
        EmployeeAccount staff = createAndSaveEmployee(tenantId, storeId, Role.STAFF);

        // Visible to `manager`: they are the target — their own account history — regardless of actor.
        EmployeeSecurityHistory managerIsTarget = saveHistory(
                tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_REAUTHENTICATION_FAILED, manager.id(), "EMPLOYEE",
                manager.id(), now());
        // Visible to `manager`: the target is a STAFF employee, regardless of actor.
        EmployeeSecurityHistory targetIsStaff = saveHistory(
                tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_STATUS_CHANGED, owner.id(), "EMPLOYEE", staff.id(), now());
        // Visible to `manager`: the target is a Kiosk device, regardless of actor.
        EmployeeSecurityHistory targetIsKiosk = saveHistory(
                tenantId, storeId, SecurityHistoryEventType.KIOSK_DEVICE_REGISTERED, owner.id(), "KIOSK_DEVICE", UUID.randomUUID(), now());
        // Not visible to `manager`: they are the actor, but the target is another MANAGER — being the actor
        // does not by itself grant visibility (ADR-02-015).
        saveHistory(
                tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_ROLE_CHANGED, manager.id(), "EMPLOYEE", otherManager.id(), now());
        // Not visible to `manager`: the target is the OWNER.
        saveHistory(
                tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_ROLE_CHANGED, owner.id(), "EMPLOYEE", owner.id(), now());

        List<EmployeeSecurityHistory> results = service.search(actorFor(manager), wideRangeRequest(null));

        assertThat(results).extracting(EmployeeSecurityHistory::id).containsExactlyInAnyOrder(
                managerIsTarget.id(), targetIsStaff.id(), targetIsKiosk.id());
    }

    @Test
    void staffCannotQuerySecurityHistory() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount staff = createAndSaveEmployee(tenantId, storeId, Role.STAFF);

        assertThatThrownBy(() -> service.search(actorFor(staff), wideRangeRequest(null)))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(SecurityHistoryProblemCode.SECURITY_HISTORY_ACCESS_FORBIDDEN);
    }

    @Test
    void queryScopeIsLimitedToTheActorsOwnTenant() {
        EmployeeAccount ownerInTenantA = createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);
        EmployeeAccount ownerInTenantB = createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);
        saveHistory(ownerInTenantB.tenantId(), ownerInTenantB.storeId(), SecurityHistoryEventType.EMPLOYEE_CREATED,
                ownerInTenantB.id(), "EMPLOYEE", UUID.randomUUID(), now());

        List<EmployeeSecurityHistory> results = service.search(actorFor(ownerInTenantA), wideRangeRequest(null));

        assertThat(results).isEmpty();
    }

    @Test
    void missingDateRangeIsRejected() {
        EmployeeAccount owner = createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

        assertThatThrownBy(() -> service.search(actorFor(owner),
                new SecurityHistorySearchRequest(null, now(), null, null, null, null, null, null, null)))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_RANGE);
    }

    @Test
    void dateRangeLongerThanNinetyDaysIsRejected() {
        EmployeeAccount owner = createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);
        Instant to = now();
        Instant from = to.minus(Duration.ofDays(91));

        assertThatThrownBy(() -> service.search(actorFor(owner),
                new SecurityHistorySearchRequest(from, to, null, null, null, null, null, null, null)))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_RANGE);
    }

    @Test
    void pageSizeOutOfBoundsIsRejected() {
        EmployeeAccount owner = createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

        assertThatThrownBy(() -> service.search(actorFor(owner), wideRangeRequest(0)))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_PAGE_SIZE);
        assertThatThrownBy(() -> service.search(actorFor(owner), wideRangeRequest(101)))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(SecurityHistoryProblemCode.INVALID_SECURITY_HISTORY_PAGE_SIZE);
    }

    @Test
    void cursorPaginationCoversEveryRecordExactlyOnceNewestFirst() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount owner = createAndSaveEmployee(tenantId, storeId, Role.OWNER);
        Instant base = now();
        List<UUID> expectedNewestFirst = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            EmployeeSecurityHistory saved = saveHistory(
                    tenantId, storeId, SecurityHistoryEventType.EMPLOYEE_CREATED, owner.id(), "EMPLOYEE",
                    UUID.randomUUID(), base.minusSeconds(i));
            expectedNewestFirst.add(saved.id());
        }

        List<UUID> collected = new ArrayList<>();
        Instant cursorOccurredAt = null;
        UUID cursorId = null;
        for (int page = 0; page < 3; page++) {
            SecurityHistorySearchRequest request = new SecurityHistorySearchRequest(
                    base.minusSeconds(10), base.plusSeconds(1), SecurityHistoryEventType.EMPLOYEE_CREATED, null,
                    null, null, cursorOccurredAt, cursorId, 2);
            List<EmployeeSecurityHistory> pageResults = service.search(actorFor(owner), request);
            if (pageResults.isEmpty()) {
                break;
            }
            pageResults.forEach(h -> collected.add(h.id()));
            EmployeeSecurityHistory last = pageResults.get(pageResults.size() - 1);
            cursorOccurredAt = last.occurredAt();
            cursorId = last.id();
        }

        assertThat(collected).containsExactlyElementsOf(expectedNewestFirst);
    }

    private EmployeeSecurityHistory saveHistory(
            UUID tenantId, UUID storeId, SecurityHistoryEventType eventType, UUID actorEmployeeId,
            String targetType, UUID targetId, Instant occurredAt) {
        EmployeeSecurityHistory history = EmployeeSecurityHistory.occur(
                UUID.randomUUID(), tenantId, storeId, eventType, actorEmployeeId, targetType, targetId,
                SecurityHistoryResult.SUCCESS, null, null, null, occurredAt);
        return employeeSecurityHistoryRepository.save(history);
    }

    private SecurityHistorySearchRequest wideRangeRequest(Integer size) {
        Instant now = now();
        return new SecurityHistorySearchRequest(
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)), null, null, null, null, null, null, size);
    }

    private Instant now() {
        return clock.instant();
    }

    private EmployeeAccount createAndSaveEmployee(UUID tenantId, UUID storeId, Role role) {
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId,
                LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)),
                "argon2-hash", role, now());
        return employeeAccountRepository.save(account);
    }

    private ActorContext actorFor(EmployeeAccount account) {
        return new ActorContext(account.tenantId(), account.storeId(), account.id(), account.role());
    }
}
