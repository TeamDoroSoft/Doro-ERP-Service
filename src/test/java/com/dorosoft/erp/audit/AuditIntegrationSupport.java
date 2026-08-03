package com.dorosoft.erp.audit;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Spring 어노테이션 없이 Audit 통합 테스트의 데이터 정리와 변환만 담당하는 순수 유틸이다. */
final class AuditIntegrationSupport {

    private static final List<String> DELETE_ORDER = List.of("audit_record_target", "audit_record");

    private AuditIntegrationSupport() {}

    static void cleanAuditTables(JdbcClient jdbcClient) {
        for (String table : DELETE_ORDER) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    static byte[] toBinary(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
