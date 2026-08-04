package com.dorosoft.erp.table;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class TableIntegrationSupport {

    private static final List<String> DELETE_ORDER =
            List.of(
                    "table_idempotency_record",
                    "table_usage_session",
                    "table_qr_credential",
                    "store_table");

    private TableIntegrationSupport() {}

    static void cleanTableTables(JdbcClient jdbcClient) {
        jdbcClient.sql("UPDATE table_qr_credential SET predecessor_id = NULL WHERE predecessor_id IS NOT NULL").update();
        for (String table : DELETE_ORDER) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    static void cleanAuditTables(JdbcClient jdbcClient) {
        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();
    }

    static void openUsageSession(JdbcClient jdbcClient, UUID sessionId, UUID tableId) {
        jdbcClient.sql(
                        """
                        INSERT INTO table_usage_session (session_id, table_id, status, opened_by, opened_at)
                        VALUES (:sessionId, :tableId, 'OPEN', :openedBy, CURRENT_TIMESTAMP(6))
                        """)
                .param("sessionId", uuidBytes(sessionId))
                .param("tableId", uuidBytes(tableId))
                .param("openedBy", uuidBytes(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .update();
    }

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
