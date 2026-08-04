package com.dorosoft.erp.store;

import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthentication;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Store 통합 테스트 공통 유틸.
 *
 * <p>Spring 어노테이션을 전혀 갖지 않는 순수 유틸이다. 통합 테스트 클래스는 각자 동일한 {@code @SpringBootTest} /
 * {@code @Import} 어노테이션을 직접 선언해 하나의 컨텍스트(=하나의 컨테이너 쌍)를 공유한다.
 */
final class StoreIntegrationSupport {

    /** FK 자식 → 부모 순서. flyway_schema_history는 절대 건드리지 않는다. */
    private static final List<String> DELETE_ORDER =
            List.of(
                    "business_hour",
                    "regular_closed_day",
                    "temporary_closure",
                    "service_window",
                    "feature_setting",
                    "notification_event_setting",
                    "store_profile");

    private StoreIntegrationSupport() {}

    static RequestPostProcessor actor(String... permissionCodes) {
        IdentityPrincipal principal = new IdentityPrincipal(
                UUID.randomUUID(),
                "local-store",
                "STORE_ADMIN",
                Set.of(permissionCodes),
                false);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new IdentityAuthentication(principal));
    }

    /** 테스트 간 격리를 위해 Store 모듈 테이블을 모두 비운다. */
    static void cleanStoreTables(JdbcClient jdbcClient) {
        for (String table : DELETE_ORDER) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    /** UUID를 Hibernate 6 기본 매핑과 동일한 BINARY(16) 바이트 배열로 변환한다. */
    static byte[] toBinary(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    static UUID toUuid(byte[] binary) {
        ByteBuffer buffer = ByteBuffer.wrap(binary);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /** 자식 테이블 검증을 위한 부모 행을 네이티브 SQL로 만든다. */
    static UUID insertStoreProfile(JdbcClient jdbcClient, String name) {
        UUID storeId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO store_profile
                            (store_id, name, address, contact, time_zone, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'Asia/Seoul', 0, NOW(6), NOW(6))
                        """)
                .param(toBinary(storeId))
                .param(name)
                .param("서울특별시 강남구 테헤란로 1")
                .param("02-1234-5678")
                .update();
        return storeId;
    }

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }
}
