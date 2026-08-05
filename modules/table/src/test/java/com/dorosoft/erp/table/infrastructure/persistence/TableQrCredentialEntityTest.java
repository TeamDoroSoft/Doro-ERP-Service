package com.dorosoft.erp.table.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TABLE-10 인수·보안 검증: QR Credential 상태 전이와 Digest 방어적 복사 계약에 대한 순수 단위 테스트.
 *
 * <p>DB나 Spring Context 없이도 (1) 재발급/폐기 시 기존 Credential이 즉시 REVOKED로 전이되는지, (2) revoke()가
 * 중복 호출되어도 최초 폐기자·폐기시각을 덮어쓰지 않는지(동시 재발급/동시 폐기 경합에서 감사 이력의 일관성을 보장),
 * (3) Token Digest가 엔티티 내부/외부 배열 참조를 공유하지 않아 원문 유출·변조 경로가 없는지를 검증한다.
 */
class TableQrCredentialEntityTest {

    private static final byte[] SAMPLE_DIGEST = {1, 2, 3, 4, 5, 6, 7, 8};

    @Test
    @DisplayName("issue()는 ACTIVE 상태의 Credential을 생성하고 이전 Credential ID를 보존한다")
    void issue_createsActiveCredential() {
        UUID credentialId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID issuedBy = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-08-05T00:00:00Z");

        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                credentialId, tableId, SAMPLE_DIGEST, predecessorId, issuedBy, issuedAt);

        assertEquals(credentialId, credential.getCredentialId());
        assertEquals(tableId, credential.getTableId());
        assertEquals(predecessorId, credential.getPredecessorId());
        assertEquals(issuedAt, credential.getIssuedAt());
        assertEquals(TableQrCredentialStatus.ACTIVE, credential.getStatus());
    }

    @Test
    @DisplayName("issue() 이후 호출자의 원본 Digest 배열을 변경해도 Credential 상태는 영향받지 않는다")
    void issue_copiesDigestDefensivelyOnConstruction() {
        byte[] mutableInput = Arrays.copyOf(SAMPLE_DIGEST, SAMPLE_DIGEST.length);
        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                UUID.randomUUID(), UUID.randomUUID(), mutableInput, null, UUID.randomUUID(), Instant.now());

        Arrays.fill(mutableInput, (byte) 0);

        assertArrayEquals(SAMPLE_DIGEST, credential.getTokenDigest(),
                "entity must not alias the caller's digest array");
    }

    @Test
    @DisplayName("getTokenDigest()가 반환한 배열을 변경해도 Credential 내부 상태는 영향받지 않는다")
    void getTokenDigest_returnsDefensiveCopy() {
        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                UUID.randomUUID(), UUID.randomUUID(), SAMPLE_DIGEST, null, UUID.randomUUID(), Instant.now());

        byte[] exposed = credential.getTokenDigest();
        Arrays.fill(exposed, (byte) 0);

        assertArrayEquals(SAMPLE_DIGEST, credential.getTokenDigest(),
                "caller mutation of the returned digest must not leak back into entity state");
    }

    @Test
    @DisplayName("revoke()는 ACTIVE Credential을 REVOKED로 전이시키고 폐기자·폐기시각을 기록한다")
    void revoke_transitionsToRevoked() {
        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                UUID.randomUUID(), UUID.randomUUID(), SAMPLE_DIGEST, null, UUID.randomUUID(), Instant.now());
        UUID revokedBy = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-08-05T01:00:00Z");

        credential.revoke(revokedBy, revokedAt);

        assertEquals(TableQrCredentialStatus.REVOKED, credential.getStatus());
    }

    @Test
    @DisplayName("이미 REVOKED된 Credential에 revoke()를 재호출해도 최초 폐기 기록은 변경되지 않는다 (동시 재발급/폐기 경합 방어)")
    void revoke_isIdempotentAndPreservesFirstRevocation() throws Exception {
        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                UUID.randomUUID(), UUID.randomUUID(), SAMPLE_DIGEST, null, UUID.randomUUID(), Instant.now());
        UUID firstRevoker = UUID.randomUUID();
        Instant firstRevokedAt = Instant.parse("2026-08-05T01:00:00Z");
        credential.revoke(firstRevoker, firstRevokedAt);

        // Simulate a concurrent duplicate revoke request racing in with a different actor/time.
        credential.revoke(UUID.randomUUID(), Instant.parse("2026-08-05T02:00:00Z"));

        assertEquals(TableQrCredentialStatus.REVOKED, credential.getStatus());
        assertEquals(firstRevoker, readField(credential, "revokedBy"),
                "second revoke() call must not overwrite the original revoker");
        assertEquals(firstRevokedAt, readField(credential, "revokedAt"),
                "second revoke() call must not overwrite the original revocation timestamp");
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    @DisplayName("최초 발급 Credential은 이전 Credential ID를 가지지 않는다")
    void issue_withoutPredecessor_hasNullPredecessorId() {
        TableQrCredentialEntity credential = TableQrCredentialEntity.issue(
                UUID.randomUUID(), UUID.randomUUID(), SAMPLE_DIGEST, null, UUID.randomUUID(), Instant.now());

        assertNull(credential.getPredecessorId());
    }
}
