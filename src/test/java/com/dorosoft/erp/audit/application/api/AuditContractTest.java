package com.dorosoft.erp.audit.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("감사 기록 계약(Port DTO)의 불변식")
class AuditContractTest {

    private static final AuditTarget TARGET =
            new AuditTarget(AuditTargetType.STORE_PROFILE, "store-profile-1");

    private static AuditRecordCommand command(
            String domain,
            String action,
            String operationId,
            int eventSequence,
            AuditTarget primaryTarget,
            List<AuditRelatedTarget> relatedTargets,
            String beforeValue,
            String afterValue,
            String reasonCode,
            String reason,
            String valueSchemaVersion) {
        return new AuditRecordCommand(
                domain,
                action,
                operationId,
                eventSequence,
                primaryTarget,
                relatedTargets,
                beforeValue,
                afterValue,
                reasonCode,
                reason,
                valueSchemaVersion);
    }

    private static AuditRecordCommand valid() {
        return AuditRecordCommand.of(
                "STORE", "STORE_PROFILE_UPDATED", "op-1", 0, TARGET, "{\"a\":1}", "{\"a\":2}", "v1");
    }

    @Nested
    @DisplayName("AuditRecordCommand")
    class RecordCommand {

        @Test
        @DisplayName("domain·action·operationId·valueSchemaVersion이 null이면 NullPointerException")
        void rejectsNullTexts() {
            assertThatThrownBy(
                            () -> command(null, "A", "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> command("D", null, "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> command("D", "A", null, 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("domain·action·operationId·valueSchemaVersion이 공백이면 IllegalArgumentException")
        void rejectsBlankTexts() {
            assertThatThrownBy(
                            () -> command("  ", "A", "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () -> command("D", "  ", "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () -> command("D", "A", "  ", 0, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, null, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("primaryTarget이 null이면 NullPointerException")
        void rejectsNullPrimaryTarget() {
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, null, null, null, null, null, null, "v1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("eventSequence가 음수면 거부하고 0은 허용한다")
        void rejectsNegativeEventSequence() {
            assertThatThrownBy(
                            () -> command("D", "A", "op", -1, TARGET, null, null, null, null, null, "v1"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(
                            command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1")
                                    .eventSequence())
                    .isZero();
        }

        @Test
        @DisplayName("before·after가 null이면 빈 JSON 객체로 정규화한다")
        void normalizesNullValuesToEmptyJsonObject() {
            AuditRecordCommand cmd =
                    command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1");

            assertThat(cmd.beforeValue()).isEqualTo("{}");
            assertThat(cmd.afterValue()).isEqualTo("{}");
        }

        @Test
        @DisplayName("relatedTargets가 null이면 빈 리스트가 된다")
        void nullRelatedTargetsBecomeEmptyList() {
            AuditRecordCommand cmd =
                    command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1");

            assertThat(cmd.relatedTargets()).isEmpty();
        }

        @Test
        @DisplayName("relatedTargets는 불변 복사되어 원본 리스트 변경에 영향받지 않는다")
        void copiesRelatedTargetsDefensively() {
            List<AuditRelatedTarget> source = new ArrayList<>();
            source.add(
                    new AuditRelatedTarget(
                            AuditRelationType.STORE, AuditTargetType.STORE_PROFILE, "store-1"));

            AuditRecordCommand cmd =
                    command("D", "A", "op", 0, TARGET, source, null, null, null, null, "v1");
            source.add(
                    new AuditRelatedTarget(
                            AuditRelationType.PRODUCT, AuditTargetType.PRODUCT, "product-1"));

            assertThat(cmd.relatedTargets()).hasSize(1);
            assertThatThrownBy(
                            () ->
                                    cmd.relatedTargets()
                                            .add(
                                                    new AuditRelatedTarget(
                                                            AuditRelationType.ORDER,
                                                            AuditTargetType.ORDER,
                                                            "order-1")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("reason은 null이거나 1~500자여야 한다")
        void validatesReasonLength() {
            assertThatCode(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .doesNotThrowAnyException();
            assertThatCode(
                            () ->
                                    command(
                                            "D",
                                            "A",
                                            "op",
                                            0,
                                            TARGET,
                                            null,
                                            null,
                                            null,
                                            null,
                                            "a".repeat(500),
                                            "v1"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, "", "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, "   ", "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    command(
                                            "D",
                                            "A",
                                            "op",
                                            0,
                                            TARGET,
                                            null,
                                            null,
                                            null,
                                            null,
                                            "a".repeat(501),
                                            "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("of()는 관련 대상·사유 없이 값을 그대로 담는다")
        void staticFactoryKeepsValues() {
            AuditRecordCommand cmd = valid();

            assertThat(cmd.domain()).isEqualTo("STORE");
            assertThat(cmd.action()).isEqualTo("STORE_PROFILE_UPDATED");
            assertThat(cmd.operationId()).isEqualTo("op-1");
            assertThat(cmd.primaryTarget()).isEqualTo(TARGET);
            assertThat(cmd.relatedTargets()).isEmpty();
            assertThat(cmd.reason()).isNull();
            assertThat(cmd.reasonCode()).isNull();
            assertThat(cmd.valueSchemaVersion()).isEqualTo("v1");
        }
    }

    @Nested
    @DisplayName("AuditContext")
    class Context {

        @Test
        @DisplayName("actor·actorRole·requestId가 null이면 NullPointerException")
        void rejectsNullTexts() {
            Instant now = Instant.parse("2026-08-03T01:00:00Z");

            assertThatThrownBy(() -> new AuditContext(null, "OWNER", now, "req-1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditContext("owner@doro", null, now, "req-1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditContext("owner@doro", "OWNER", now, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("actor·actorRole·requestId가 공백이면 IllegalArgumentException")
        void rejectsBlankTexts() {
            Instant now = Instant.parse("2026-08-03T01:00:00Z");

            assertThatThrownBy(() -> new AuditContext("  ", "OWNER", now, "req-1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AuditContext("owner@doro", "  ", now, "req-1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AuditContext("owner@doro", "OWNER", now, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("occurredAt이 null이면 NullPointerException")
        void rejectsNullOccurredAt() {
            assertThatThrownBy(() -> new AuditContext("owner@doro", "OWNER", null, "req-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("정상 값은 그대로 보존한다")
        void keepsValidValues() {
            Instant now = Instant.parse("2026-08-03T01:00:00Z");

            AuditContext context = new AuditContext("owner@doro", "OWNER", now, "req-1");

            assertThat(context.actor()).isEqualTo("owner@doro");
            assertThat(context.actorRole()).isEqualTo("OWNER");
            assertThat(context.occurredAt()).isEqualTo(now);
            assertThat(context.requestId()).isEqualTo("req-1");
        }
    }

    @Nested
    @DisplayName("AuditWriteResult")
    class WriteResult {

        private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");

        @Test
        @DisplayName("status·auditId·occurredAt이 null이면 NullPointerException")
        void rejectsNulls() {
            assertThatThrownBy(() -> new AuditWriteResult(null, "audit-1", NOW))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditWriteResult(AuditWriteStatus.RECORDED, null, NOW))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> new AuditWriteResult(AuditWriteStatus.RECORDED, "audit-1", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("auditId가 공백이면 IllegalArgumentException")
        void rejectsBlankAuditId() {
            assertThatThrownBy(() -> new AuditWriteResult(AuditWriteStatus.RECORDED, "  ", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("멱등 재호출 결과도 동일한 불변식을 지킨다")
        void allowsAlreadyRecorded() {
            AuditWriteResult result =
                    new AuditWriteResult(AuditWriteStatus.ALREADY_RECORDED, "audit-1", NOW);

            assertThat(result.status()).isEqualTo(AuditWriteStatus.ALREADY_RECORDED);
            assertThat(result.auditId()).isEqualTo("audit-1");
            assertThat(result.occurredAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("AuditTarget · AuditRelatedTarget")
    class Targets {

        @Test
        @DisplayName("AuditTarget의 targetType·targetId가 null이면 NullPointerException")
        void auditTargetRejectsNulls() {
            assertThatThrownBy(() -> new AuditTarget(null, "id-1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditTarget(AuditTargetType.ORDER, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("AuditTarget의 targetId가 공백이면 IllegalArgumentException")
        void auditTargetRejectsBlankId() {
            assertThatThrownBy(() -> new AuditTarget(AuditTargetType.ORDER, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AuditRelatedTarget의 relationType·targetType·targetId가 null이면 NullPointerException")
        void relatedTargetRejectsNulls() {
            assertThatThrownBy(
                            () -> new AuditRelatedTarget(null, AuditTargetType.ORDER, "id-1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditRelatedTarget(AuditRelationType.ORDER, null, "id-1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () ->
                                    new AuditRelatedTarget(
                                            AuditRelationType.ORDER, AuditTargetType.ORDER, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("AuditRelatedTarget의 targetId가 공백이면 IllegalArgumentException")
        void relatedTargetRejectsBlankId() {
            assertThatThrownBy(
                            () ->
                                    new AuditRelatedTarget(
                                            AuditRelationType.ORDER, AuditTargetType.ORDER, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
