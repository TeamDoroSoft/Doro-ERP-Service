package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the OWNER/MANAGER authorization matrix, the self-account-change block, Tenant-scoped lookup,
 * and last-active-OWNER protection under concurrent role changes (ADR-02-010).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
@Testcontainers
class EmployeeManagementServiceIntegrationTest {

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
    private EmployeeManagementService service;

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void ownerCanCreateEmployeesOfAnyRole() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE));

        for (Role role : Role.values()) {
            EmployeeAccount created = service.createEmployee(owner, freshLoginId(), "argon2-hash", role);
            assertThat(created.role()).isEqualTo(role);
            assertThat(created.tenantId()).isEqualTo(tenantId);
            assertThat(created.storeId()).isEqualTo(storeId);
        }
    }

    @Test
    void managerCanOnlyCreateStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext manager = actorFor(createAndSave(tenantId, storeId, Role.MANAGER, EmployeeStatus.ACTIVE));

        EmployeeAccount created = service.createEmployee(manager, freshLoginId(), "argon2-hash", Role.STAFF);
        assertThat(created.role()).isEqualTo(Role.STAFF);

        assertThatThrownBy(() -> service.createEmployee(manager, freshLoginId(), "argon2-hash", Role.MANAGER))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
        assertThatThrownBy(() -> service.createEmployee(manager, freshLoginId(), "argon2-hash", Role.OWNER))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void staffCannotCreateAnyone() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext staff = actorFor(createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE));

        assertThatThrownBy(() -> service.createEmployee(staff, freshLoginId(), "argon2-hash", Role.STAFF))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void ownerCanChangeAnyEmployeesRole() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE));
        EmployeeAccount staff = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE);

        EmployeeAccount promoted = service.changeRole(owner, staff.id(), Role.MANAGER);

        assertThat(promoted.role()).isEqualTo(Role.MANAGER);
    }

    @Test
    void managerCanOnlyKeepStaffAsStaffAndCannotPromote() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext manager = actorFor(createAndSave(tenantId, storeId, Role.MANAGER, EmployeeStatus.ACTIVE));
        EmployeeAccount staff = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeRole(manager, staff.id(), Role.MANAGER))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void managerCannotChangeOwnerOrManagerRole() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext manager = actorFor(createAndSave(tenantId, storeId, Role.MANAGER, EmployeeStatus.ACTIVE));
        EmployeeAccount otherManager = createAndSave(tenantId, storeId, Role.MANAGER, EmployeeStatus.ACTIVE);
        EmployeeAccount owner = createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeRole(manager, otherManager.id(), Role.STAFF))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
        assertThatThrownBy(() -> service.changeStatus(manager, owner.id(), EmployeeStatus.INACTIVE))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void staffCannotChangeAnyonesRoleOrStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext staff = actorFor(createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE));
        EmployeeAccount otherStaff = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeStatus(staff, otherStaff.id(), EmployeeStatus.INACTIVE))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void selfRoleAndStatusChangeIsRejectedEvenForOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount ownerAccount = createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE);
        ActorContext owner = actorFor(ownerAccount);

        assertThatThrownBy(() -> service.changeRole(owner, ownerAccount.id(), Role.MANAGER))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.SELF_ACCOUNT_ADMIN_CHANGE_NOT_ALLOWED);
        assertThatThrownBy(() -> service.changeStatus(owner, ownerAccount.id(), EmployeeStatus.INACTIVE))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.SELF_ACCOUNT_ADMIN_CHANGE_NOT_ALLOWED);
    }

    @Test
    void targetInAnotherTenantIsTreatedAsNotFoundWithoutRevealingExistence() {
        ActorContext ownerInTenantA = actorFor(
                createAndSave(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER, EmployeeStatus.ACTIVE));
        EmployeeAccount staffInTenantB =
                createAndSave(UUID.randomUUID(), UUID.randomUUID(), Role.STAFF, EmployeeStatus.ACTIVE);

        assertThatThrownBy(() -> service.changeRole(ownerInTenantA, staffInTenantB.id(), Role.MANAGER))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND);
    }

    @Test
    void ownerCanDeactivateAndReactivateStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE));
        EmployeeAccount staff = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE);

        EmployeeAccount deactivated = service.changeStatus(owner, staff.id(), EmployeeStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(EmployeeStatus.INACTIVE);

        EmployeeAccount reactivated = service.changeStatus(owner, staff.id(), EmployeeStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void concurrentDemotionOfBothLastActiveOwnersLeavesExactlyOneActiveOwner() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        EmployeeAccount ownerA = createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE);
        EmployeeAccount ownerB = createAndSave(tenantId, storeId, Role.OWNER, EmployeeStatus.ACTIVE);
        ActorContext actingAsA = actorFor(ownerA);
        ActorContext actingAsB = actorFor(ownerB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> aDemotesB = () -> service.changeRole(actingAsA, ownerB.id(), Role.MANAGER);
            Callable<Object> bDemotesA = () -> service.changeRole(actingAsB, ownerA.id(), Role.MANAGER);

            Future<Object> futureB = executor.submit(aDemotesB);
            Future<Object> futureA = executor.submit(bDemotesA);

            Object outcomeB = resultOrFailure(futureB);
            Object outcomeA = resultOrFailure(futureA);

            long successCount = Stream.of(outcomeA, outcomeB)
                    .filter(EmployeeAccount.class::isInstance)
                    .count();
            long rejectedAsLastOwnerCount = Stream.of(outcomeA, outcomeB)
                    .filter(ProblemAwareException.class::isInstance)
                    .map(ProblemAwareException.class::cast)
                    .filter(ex -> ex.code() == EmployeeManagementProblemCode.LAST_ACTIVE_OWNER_REQUIRED)
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(rejectedAsLastOwnerCount).isEqualTo(1);

            List<EmployeeAccount> remainingActiveOwners = new TransactionTemplate(transactionManager)
                    .execute(status -> employeeAccountRepository.findActiveOwnersForUpdate(tenantId));
            assertThat(remainingActiveOwners).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object resultOrFailure(Future<Object> future) throws InterruptedException {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            throw new AssertionError("Concurrent role change did not complete in time", e);
        }
    }

    private EmployeeAccount createAndSave(UUID tenantId, UUID storeId, Role role, EmployeeStatus status) {
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId, freshLoginId(), "argon2-hash", role, clock.instant());
        if (status == EmployeeStatus.INACTIVE) {
            account = account.changeStatus(EmployeeStatus.INACTIVE, clock.instant());
        }
        return employeeAccountRepository.save(account);
    }

    private ActorContext actorFor(EmployeeAccount account) {
        return new ActorContext(account.tenantId(), account.storeId(), account.id(), account.role());
    }

    private LoginId freshLoginId() {
        return LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }
}
