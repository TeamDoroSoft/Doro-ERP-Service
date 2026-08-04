package com.dorosoft.erp.table;

import java.util.List;
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

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }
}
