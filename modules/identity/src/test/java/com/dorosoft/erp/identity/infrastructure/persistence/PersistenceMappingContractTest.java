package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.CredentialJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeAccountJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeRoleJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.IdentityIdempotencyJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.IdentitySecurityEventJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.PermissionJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.RoleJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.RolePermissionJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.EmployeeAccountSpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.IdentityIdempotencySpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.RoleSpringDataRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceMappingContractTest {
    @Test
    void allNineIdentityTablesHaveJpaMappings() {
        List<Class<?>> entities = List.of(
                EmployeeAccountJpaEntity.class, CredentialJpaEntity.class,
                PasswordHistoryJpaEntity.class, RoleJpaEntity.class, PermissionJpaEntity.class,
                EmployeeRoleJpaEntity.class, RolePermissionJpaEntity.class,
                IdentityIdempotencyJpaEntity.class, IdentitySecurityEventJpaEntity.class
        );
        assertEquals(9, entities.size());
        entities.forEach(type -> assertNotNull(type.getAnnotation(Entity.class)));
    }

    @Test
    void accountCredentialAndRoleUseOptimisticVersions() {
        assertTrue(hasVersionField(EmployeeAccountJpaEntity.class));
        assertTrue(hasVersionField(CredentialJpaEntity.class));
        assertTrue(hasVersionField(RoleJpaEntity.class));
    }

    @Test
    void accountRoleAndIdempotencyContentionUsePessimisticWriteLocks() throws Exception {
        assertPessimisticWrite(EmployeeAccountSpringDataRepository.class.getMethod(
                "findByIdForUpdate", String.class
        ));
        assertPessimisticWrite(RoleSpringDataRepository.class.getMethod(
                "findByCodeForUpdate", String.class
        ));
        assertPessimisticWrite(IdentityIdempotencySpringDataRepository.class.getMethod(
                "findForUpdate",
                com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation.class,
                byte[].class
        ));
    }

    @Test
    void idempotencyClaimUsesTheOperationAndDigestUniqueKeyWithoutASecondStore() throws Exception {
        Method insert = java.util.Arrays.stream(IdentityIdempotencySpringDataRepository.class.getMethods())
                .filter(method -> method.getName().equals("tryInsertProcessing"))
                .findFirst()
                .orElseThrow();
        Query query = insert.getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("INSERT IGNORE INTO identity_idempotency_record"));
        assertTrue(query.value().contains(":operation"));
        assertTrue(query.value().contains(":keyDigest"));
    }

    private static boolean hasVersionField(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(Version.class));
    }

    private static void assertPessimisticWrite(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
