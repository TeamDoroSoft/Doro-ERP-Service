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
        for (String table : DELETE_ORDER) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }
}
