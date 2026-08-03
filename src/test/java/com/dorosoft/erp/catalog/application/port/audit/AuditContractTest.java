package com.dorosoft.erp.catalog.application.port.audit;

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

    private static final AuditTarget TARGET = new AuditTarget(AuditTargetType.PRODUCT, "product-1");

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
                "CATALOG",
                "PRODUCT_PRICE_CHANGED",
                "op-1",
                0,
                TARGET,
                "{\"basePrice\":4500}",
                "{\"basePrice\":5000}",
                "v1");
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

            assertThatCode(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("relatedTargets가 null이면 빈 List로 대체하고 방어적 복사한다")
        void defaultsRelatedTargetsAndCopiesDefensively() {
            AuditRecordCommand nullRelated =
                    command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1");
            assertThat(nullRelated.relatedTargets()).isEmpty();

            List<AuditRelatedTarget> mutable = new ArrayList<>();
            mutable.add(new AuditRelatedTarget(AuditRelationType.CATEGORY, AuditTargetType.CATEGORY, "category-1"));
            AuditRecordCommand withRelated =
                    command("D", "A", "op", 0, TARGET, mutable, null, null, null, null, "v1");
            mutable.clear();

            assertThat(withRelated.relatedTargets()).hasSize(1);
        }

        @Test
        @DisplayName("beforeValue·afterValue가 null이면 빈 JSON Object로 대체한다")
        void defaultsBeforeAfterToEmptyJsonObject() {
            AuditRecordCommand cmd = command("D", "A", "op", 0, TARGET, null, null, null, null, null, "v1");

            assertThat(cmd.beforeValue()).isEqualTo("{}");
            assertThat(cmd.afterValue()).isEqualTo("{}");
        }

        @Test
        @DisplayName("reason이 빈 문자열이거나 500자를 초과하면 IllegalArgumentException")
        void rejectsInvalidReasonLength() {
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, "", "v1"))
                    .isInstanceOf(IllegalArgumentException.class);

            String tooLong = "a".repeat(501);
            assertThatThrownBy(
                            () -> command("D", "A", "op", 0, TARGET, null, null, null, null, tooLong, "v1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reason이 null이면 허용한다 (선택 필드)")
        void allowsNullReason() {
            assertThatCode(() -> valid()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("of()는 관련 대상·사유 없이 단순 Command를 만든다")
        void ofBuildsSimpleCommand() {
            AuditRecordCommand cmd = valid();

            assertThat(cmd.domain()).isEqualTo("CATALOG");
            assertThat(cmd.action()).isEqualTo("PRODUCT_PRICE_CHANGED");
            assertThat(cmd.relatedTargets()).isEmpty();
            assertThat(cmd.reasonCode()).isNull();
            assertThat(cmd.reason()).isNull();
        }
    }

    @Nested
    @DisplayName("AuditTarget·AuditRelatedTarget")
    class Targets {

        @Test
        @DisplayName("targetId가 공백이면 IllegalArgumentException")
        void rejectsBlankTargetId() {
            assertThatThrownBy(() -> new AuditTarget(AuditTargetType.PRODUCT, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    new AuditRelatedTarget(
                                            AuditRelationType.CATEGORY, AuditTargetType.CATEGORY, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("targetType이 null이면 NullPointerException")
        void rejectsNullTargetType() {
            assertThatThrownBy(() -> new AuditTarget(null, "product-1"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("AuditContext")
    class Context {

        @Test
        @DisplayName("actor·actorRole·requestId가 공백이면 IllegalArgumentException")
        void rejectsBlankFields() {
            assertThatThrownBy(() -> new AuditContext("  ", "ADMIN", Instant.now(), "req-1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AuditContext("actor-1", "  ", Instant.now(), "req-1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AuditContext("actor-1", "ADMIN", Instant.now(), "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("occurredAt이 null이면 NullPointerException")
        void rejectsNullOccurredAt() {
            assertThatThrownBy(() -> new AuditContext("actor-1", "ADMIN", null, "req-1"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("AuditWriteResult")
    class WriteResult {

        @Test
        @DisplayName("auditId가 공백이면 IllegalArgumentException")
        void rejectsBlankAuditId() {
            assertThatThrownBy(
                            () -> new AuditWriteResult(AuditWriteStatus.RECORDED, "  ", Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("status·occurredAt이 null이면 NullPointerException")
        void rejectsNullStatusOrOccurredAt() {
            assertThatThrownBy(() -> new AuditWriteResult(null, "audit-1", Instant.now()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AuditWriteResult(AuditWriteStatus.RECORDED, "audit-1", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
