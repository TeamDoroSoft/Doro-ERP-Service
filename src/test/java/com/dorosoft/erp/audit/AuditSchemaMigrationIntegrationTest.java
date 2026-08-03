package com.dorosoft.erp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Audit 스키마 Migration 제약 통합 테스트")
class AuditSchemaMigrationIntegrationTest {

    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        AuditIntegrationSupport.cleanAuditTables(jdbcClient);
    }

    @Test
    @DisplayName("(domain, operation_id, event_sequence) 중복은 거부된다")
    void 감사_이벤트_자연키는_unique다() {
        insertAudit(UUID.randomUUID(), "op-unique", 0);

        assertThatThrownBy(() -> insertAudit(UUID.randomUUID(), "op-unique", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_audit_record_operation_sequence");
    }

    @Test
    @DisplayName("event_sequence 음수는 CHECK 제약으로 거부된다")
    void 이벤트_순번은_0_이상이다() {
        assertThatThrownBy(() -> insertAudit(UUID.randomUUID(), "op-check", -1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_audit_record_event_sequence");
    }

    @Test
    @DisplayName("audit_record_target의 audit_id FK와 RESTRICT 삭제 규칙이 존재한다")
    void 대상_테이블의_fk가_존재한다() {
        String deleteRule = jdbcClient.sql("""
                        SELECT rc.DELETE_RULE
                          FROM information_schema.REFERENTIAL_CONSTRAINTS rc
                         WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
                           AND rc.TABLE_NAME = 'audit_record_target'
                           AND rc.CONSTRAINT_NAME = 'fk_audit_record_target_record'
                        """)
                .query(String.class)
                .single();

        assertThat(deleteRule).isEqualTo("RESTRICT");
    }

    private void insertAudit(UUID auditId, String operationId, int eventSequence) {
        jdbcClient.sql("""
                        INSERT INTO audit_record
                            (audit_id, domain, action, operation_id, event_sequence,
                             actor_type, actor_id, actor_role_snapshot, target_type, target_id,
                             before_value, after_value, value_schema_version, retention_class,
                             retention_until, payload_hmac, request_id, occurred_at, recorded_at)
                        VALUES (?, 'STORE', 'UPDATED', ?, ?, 'AUTHENTICATED_USER', 'actor-1', 'OWNER',
                                'STORE_PROFILE', 'store-1', JSON_OBJECT(), JSON_OBJECT(), 'v1',
                                'THREE_YEARS', TIMESTAMP('2029-08-03 00:00:00'), ?, 'req-1',
                                TIMESTAMP('2026-08-03 00:00:00'), TIMESTAMP('2026-08-03 00:00:01'))
                        """)
                .param(AuditIntegrationSupport.toBinary(auditId))
                .param(operationId)
                .param(eventSequence)
                .param(new byte[32])
                .update();
    }
}
