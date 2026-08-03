package com.dorosoft.erp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditTarget;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import com.dorosoft.erp.audit.application.api.AuditWriteStatus;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.infrastructure.persistence.AuditEventConflictException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("JPA 감사 기록 Writer 통합 테스트")
class JpaAuditWriterIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T01:02:03.123456Z");

    @Autowired private AuditWriter auditWriter;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void 테이블을_비운다() {
        AuditIntegrationSupport.cleanAuditTables(jdbcClient);
    }

    @Test
    @DisplayName("신규 기록은 본문과 중복 제거된 기본·관련 대상을 저장한다")
    void 신규_기록을_저장한다() {
        var result = auditWriter.record(command("op-new", "{\"name\":\"after\"}"), context());

        assertThat(result.status()).isEqualTo(AuditWriteStatus.RECORDED);
        assertThat(result.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record")).isEqualTo(1);
        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record_target")).isEqualTo(2);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM audit_record_target WHERE relation_type = 'PRIMARY'")
                        .query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키와 payload의 재호출은 기존 결과를 반환하고 새 행을 쓰지 않는다")
    void 같은_payload는_멱등하게_처리한다() {
        var first = auditWriter.record(command("op-idempotent", "{\"v\":1}"), context());
        var second = auditWriter.record(command("op-idempotent", "{\"v\":1}"), context());

        assertThat(second.status()).isEqualTo(AuditWriteStatus.ALREADY_RECORDED);
        assertThat(second.auditId()).isEqualTo(first.auditId());
        assertThat(second.occurredAt()).isEqualTo(first.occurredAt());
        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record")).isEqualTo(1);
        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record_target")).isEqualTo(2);
    }

    @Test
    @DisplayName("한 호출자 트랜잭션 안의 같은 키·다른 payload 충돌은 최초 기록까지 롤백한다")
    void 다른_payload는_충돌하고_전체_롤백한다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    auditWriter.record(command("op-conflict", "{\"v\":1}"), context());
                    auditWriter.record(command("op-conflict", "{\"v\":2}"), context());
                }))
                .isInstanceOf(AuditEventConflictException.class);

        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record")).isZero();
        assertThat(AuditIntegrationSupport.countOf(jdbcClient, "audit_record_target")).isZero();
    }

    @Test
    @DisplayName("retentionUntil은 occurredAt의 UTC 기준 정확히 3년 뒤다")
    void 보존_만료_시각을_계산한다() {
        auditWriter.record(command("op-retention", "{}"), context());

        Instant retentionUntil = jdbcClient.sql("SELECT retention_until FROM audit_record")
                .query(Instant.class)
                .single();
        assertThat(retentionUntil).isEqualTo(Instant.parse("2029-08-03T01:02:03.123456Z"));
    }

    private static AuditRecordCommand command(String operationId, String afterValue) {
        AuditRelatedTarget related = new AuditRelatedTarget(
                AuditRelationType.STORE, AuditTargetType.STORE_PROFILE, "store-1");
        return new AuditRecordCommand(
                "STORE", "STORE_PROFILE_UPDATED", operationId, 0,
                new AuditTarget(AuditTargetType.STORE_PROFILE, "profile-1"),
                List.of(related, related), "{\"name\":\"before\"}", afterValue,
                "USER_REQUEST", "매장 정보 수정", "v1");
    }

    private static AuditContext context() {
        return new AuditContext("owner@doro", "OWNER", OCCURRED_AT, "req-audit-1");
    }
}
