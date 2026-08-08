package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Verifies real deletion of every session under an employee's Principal Index Key against Redis. */
@SpringBootTest
@Testcontainers
class SessionInvalidatorIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @Autowired
    private SessionInvalidator sessionInvalidator;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Test
    void deletesEverySessionRegisteredUnderThePrincipalIndexKey() {
        SessionPrincipalIndexKey key = new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID());
        createIndexedSession(key);
        createIndexedSession(key);
        createIndexedSession(new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID()));

        SessionInvalidationResult result = sessionInvalidator.invalidateAll(key);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, key.value())).isEmpty();
    }

    @Test
    void aPrincipalWithNoSessionsIsAHarmlessNoOp() {
        SessionPrincipalIndexKey key = new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID());

        SessionInvalidationResult result = sessionInvalidator.invalidateAll(key);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void deletingOnePrincipalsSessionsDoesNotAffectAnothers() {
        SessionPrincipalIndexKey keyToDelete = new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID());
        SessionPrincipalIndexKey keyToKeep = new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID());
        createIndexedSession(keyToDelete);
        createIndexedSession(keyToKeep);

        sessionInvalidator.invalidateAll(keyToDelete);

        assertThat(sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, keyToKeep.value())).hasSize(1);
    }

    private void createIndexedSession(SessionPrincipalIndexKey key) {
        RedisIndexedSessionRepository.RedisSession session = sessionRepository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, key.value());
        sessionRepository.save(session);
    }
}
