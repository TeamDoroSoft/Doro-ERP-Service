package com.dorosoft.erp.identity;

import com.dorosoft.erp.identity.domain.role.PermissionCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityMigrationContractTest {
    private static final String MIGRATION =
            "db/migration/V202608040527__identity_schema_and_seed.sql";
    private static final List<String> TABLES = List.of(
            "employee_account", "credential", "credential_password_history", "role", "permission",
            "employee_role", "role_permission", "identity_idempotency_record", "identity_security_event"
    );

    @Test
    void migrationDefinesOnlyTheNineIdentityTables() throws IOException {
        String sql = sql();
        TABLES.forEach(table -> assertTrue(
                Pattern.compile("CREATE\\s+TABLE\\s+" + table + "\\s*\\(", Pattern.CASE_INSENSITIVE)
                        .matcher(sql).find(),
                () -> "missing Identity table: " + table
        ));
        assertEquals(9, count(sql, "CREATE\\s+TABLE\\s+"));
        assertFalse(sql.contains("employee_permission"));
        assertFalse(sql.contains("role_parent"));
        assertFalse(sql.contains("identity_audit"));
        assertFalse(sql.contains("rate_limit_history"));
    }

    @Test
    void columnsAndLengthsMatchTheCanonicalDataModel() throws IOException {
        String compact = compact(sql());
        assertTrue(compact.contains("login_id_normalized VARCHAR(50) NOT NULL"));
        assertFalse(compact.contains("login_id_raw"));
        assertTrue(compact.contains("employee_account_id CHAR(36) NOT NULL, password_hash VARCHAR(255)"));
        assertTrue(compact.contains("password_changed_at TIMESTAMP(6) NOT NULL"));
        assertTrue(compact.contains("code VARCHAR(40) NOT NULL"));
        assertTrue(compact.contains("code VARCHAR(120) NOT NULL"));
        assertFalse(compact.contains("is_system"));
        assertFalse(compact.contains("is_active"));
        assertFalse(compact.contains("sequence_no"));
    }

    @Test
    void permissionAndRoleSeedsMatchTheExactFinalSets() throws IOException {
        String sql = sql();
        Set<String> permissions = permissionSeedCodes(sql);
        assertEquals(PermissionCatalog.all(), permissions);
        assertEquals(63, permissions.size());

        assertEquals(expectedEmployee(), rolePermissionSeed(sql, "EMPLOYEE"));
        assertEquals(union(expectedEmployee(), Set.of("inventory.receive", "inventory.alert.read")),
                rolePermissionSeed(sql, "INVENTORY_OPERATOR"));
        assertEquals(union(expectedEmployee(), Set.of(
                "reservation.decide", "reservation.update", "reservation.cancel",
                "reservation.no_show", "reservation.change.decide", "reservation.policy.read"
        )), rolePermissionSeed(sql, "RESERVATION_OPERATOR"));
        assertTrue(Pattern.compile(
                "CROSS\\s+JOIN\\s+permission\\s+p\\s+WHERE\\s+r.code\\s*=\\s*'ADMIN'",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(sql).find());
    }

    @Test
    void databaseConstraintsProtectCardinalityConcurrencyAndEventRegistry() throws IOException {
        String sql = sql();
        String compact = compact(sql);
        assertTrue(compact.contains("PRIMARY KEY (employee_account_id)"));
        assertTrue(compact.contains("PRIMARY KEY (role_id, permission_id)"));
        assertTrue(compact.contains("UNIQUE (operation, key_digest)"));
        assertTrue(compact.contains("assignment_source = 'BOOTSTRAP' AND assigned_by IS NULL"));
        assertTrue(compact.contains("assignment_source = 'ADMIN' AND assigned_by IS NOT NULL"));
        assertTrue(compact.contains("locked_until = TIMESTAMPADD(MINUTE, 5, locked_at)"));
        assertTrue(compact.contains("expires_at = TIMESTAMPADD(HOUR, 24, created_at)"));
        assertTrue(compact.contains("status = 'PROCESSING' AND target_account_id IS NULL"));
        assertTrue(compact.contains("http_status = 201 AND response_ciphertext IS NOT NULL AND response_nonce IS NOT NULL"));
        assertTrue(compact.contains("http_status = 204 AND response_ciphertext IS NULL AND response_nonce IS NULL"));

        for (String event : List.of(
                "LOGIN_SUCCEEDED", "LOGIN_FAILED", "LOGIN_REJECTED", "LOGIN_RATE_LIMITED",
                "ACCOUNT_TEMPORARILY_LOCKED", "ACCOUNT_PERMANENTLY_LOCKED",
                "LOGIN_SESSION_CREATE_FAILED", "LOGOUT_SUCCEEDED", "PASSWORD_CHANGED", "PASSWORD_RESET"
        )) {
            assertTrue(sql.contains("'" + event + "'"));
        }
        assertFalse(sql.contains("UNKNOWN"));
    }

    @Test
    void seedUsesNaturalKeyUpsertAndMaterializesEveryFinalRoleSet() throws IOException {
        String sql = sql();
        String compact = compact(sql);
        assertTrue(compact.contains("ON DUPLICATE KEY UPDATE description = VALUES(description)"));
        assertTrue(compact.contains("ON DUPLICATE KEY UPDATE name = VALUES(name), active = VALUES(active)"));
        assertTrue(compact.contains("DELETE rp FROM role_permission rp JOIN role r"));
        assertEquals(4, count(sql, "INSERT\\s+INTO\\s+role_permission"));
    }

    private static Set<String> expectedEmployee() {
        return Set.of(
                "table.read", "table.session.manage", "table.order.read", "order.create", "order.read",
                "order.status.update", "inventory.read", "waiting.read", "waiting.call", "waiting.admit",
                "waiting.cancel", "waiting.no_show", "reservation.read", "reservation.visit.update"
        );
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static Set<String> permissionSeedCodes(String sql) {
        Matcher block = Pattern.compile(
                "INSERT\\s+INTO\\s+permission.*?VALUES(.*?)ON\\s+DUPLICATE\\s+KEY",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(sql);
        assertTrue(block.find());
        Matcher code = Pattern.compile("UUID\\(\\),\\s*'([^']+)'", Pattern.CASE_INSENSITIVE)
                .matcher(block.group(1));
        Set<String> result = new HashSet<>();
        while (code.find()) result.add(code.group(1));
        return Set.copyOf(result);
    }

    private static Set<String> rolePermissionSeed(String sql, String roleCode) {
        Pattern wherePattern = Pattern.compile(
                "WHERE\\s+r.code\\s*=\\s*'" + roleCode + "'",
                Pattern.CASE_INSENSITIVE
        );
        Matcher where = wherePattern.matcher(sql);
        assertTrue(where.find(), () -> "missing role permission seed: " + roleCode);
        String beforeWhere = sql.substring(0, where.start());
        Matcher opening = Pattern.compile(
                "JOIN\\s+permission\\s+p\\s+ON\\s+p.code\\s+IN\\s*\\(",
                Pattern.CASE_INSENSITIVE
        ).matcher(beforeWhere);
        int valuesStart = -1;
        while (opening.find()) valuesStart = opening.end();
        assertTrue(valuesStart >= 0, () -> "missing permission list for role: " + roleCode);
        Matcher code = Pattern.compile("'([^']+)'").matcher(beforeWhere.substring(valuesStart));
        Set<String> result = new HashSet<>();
        while (code.find()) result.add(code.group(1));
        return Set.copyOf(result);
    }

    private static int count(String value, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String sql() throws IOException {
        try (var stream = IdentityMigrationContractTest.class.getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(stream, "migration resource must exist");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
