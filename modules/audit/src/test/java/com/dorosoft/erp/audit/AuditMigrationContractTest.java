package com.dorosoft.erp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AuditMigrationContractTest {
    private static final String MIGRATION =
            "db/migration/V202608040526__audit_feature17_audit_privacy_schema.sql";

    @Test
    void definesOnlyTheFourFeature17Tables() throws IOException {
        String sql = sql();
        for (String table : List.of(
                "audit_record", "audit_record_target",
                "privacy_access_log", "privacy_access_log_subject")) {
            assertTrue(Pattern.compile(
                    "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+" + table + "\\s*\\(",
                    Pattern.CASE_INSENSITIVE).matcher(sql).find(), () -> "missing Audit table: " + table);
        }
        assertEquals(4, count(sql, "CREATE\\s+TABLE"));
        assertFalse(sql.toLowerCase().contains("identity_audit"));
    }

    @Test
    void preservesAppendOnlyIntegrityAndPrivacyKeyVersionContracts() throws IOException {
        String compact = sql().replaceAll("\\s+", " ").trim();
        assertTrue(compact.contains("UNIQUE KEY uq_audit_record_operation (domain, operation_id, event_sequence)"));
        assertTrue(compact.contains("payload_hmac BINARY(32) NOT NULL"));
        assertTrue(compact.contains("actor_role_snapshot VARCHAR(40) NOT NULL"));
        assertTrue(compact.contains("client_address_ciphertext VARBINARY(512) NULL"));
        assertTrue(compact.contains("client_address_key_version VARCHAR(50) NULL"));
        assertTrue(compact.contains("ON DELETE RESTRICT"));
        assertFalse(compact.contains("ON DELETE CASCADE"));
    }

    private static int count(String value, String regex) {
        var matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String sql() throws IOException {
        try (var stream = AuditMigrationContractTest.class.getClassLoader()
                .getResourceAsStream(MIGRATION)) {
            assertNotNull(stream, "migration resource must exist");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
