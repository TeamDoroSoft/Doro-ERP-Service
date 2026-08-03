package com.dorosoft.erp.audit.infrastructure.persistence;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditWriteResult;
import com.dorosoft.erp.audit.application.api.AuditWriteStatus;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** 호출자(생산 모듈)의 트랜잭션 전파에 참여하며 자체 트랜잭션 경계를 만들지 않는다. */
@Component
@EnableConfigurationProperties(AuditProperties.class)
public class JpaAuditWriter implements AuditWriter {

    private static final String ACTOR_TYPE = "AUTHENTICATED_USER";
    private static final String RETENTION_CLASS = "THREE_YEARS";

    private final AuditRecordJpaRepository repository;
    private final AuditProperties properties;
    private final Clock clock;

    JpaAuditWriter(AuditRecordJpaRepository repository, AuditProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
        byte[] payloadHmac = hmac(canonical(command, context));

        var existing = repository.findByDomainAndOperationIdAndEventSequence(
                command.domain(), command.operationId(), command.eventSequence());
        if (existing.isPresent()) {
            AuditRecordEntity record = existing.get();
            if (Arrays.equals(record.getPayloadHmac(), payloadHmac)) {
                return new AuditWriteResult(
                        AuditWriteStatus.ALREADY_RECORDED,
                        record.getAuditId().toString(),
                        record.getOccurredAt());
            }
            throw new AuditEventConflictException(
                    command.domain(), command.operationId(), command.eventSequence());
        }

        UUID auditId = UUID.randomUUID();
        Instant retentionUntil = ZonedDateTime.ofInstant(context.occurredAt(), ZoneOffset.UTC)
                .plusYears(3)
                .toInstant();
        AuditRecordEntity entity = new AuditRecordEntity(
                auditId,
                command.domain(),
                command.action(),
                command.operationId(),
                command.eventSequence(),
                ACTOR_TYPE,
                context.actor(),
                context.actorRole(),
                command.primaryTarget().targetType().name(),
                command.primaryTarget().targetId(),
                command.beforeValue(),
                command.afterValue(),
                command.reasonCode(),
                command.reason(),
                command.valueSchemaVersion(),
                RETENTION_CLASS,
                retentionUntil,
                payloadHmac,
                context.requestId(),
                context.occurredAt(),
                clock.instant());

        addTargets(entity, auditId, command, context.occurredAt());
        repository.saveAndFlush(entity);
        return new AuditWriteResult(AuditWriteStatus.RECORDED, auditId.toString(), context.occurredAt());
    }

    private void addTargets(
            AuditRecordEntity entity, UUID auditId, AuditRecordCommand command, Instant occurredAt) {
        Set<TargetKey> targets = new LinkedHashSet<>();
        targets.add(new TargetKey(
                AuditRelationType.PRIMARY.name(),
                command.primaryTarget().targetType().name(),
                command.primaryTarget().targetId()));
        for (AuditRelatedTarget related : command.relatedTargets()) {
            targets.add(new TargetKey(
                    related.relationType().name(), related.targetType().name(), related.targetId()));
        }
        for (TargetKey target : targets) {
            entity.getTargets().add(new AuditRecordTargetEntity(
                    new AuditRecordTargetId(
                            auditId, target.relationType(), target.targetType(), target.targetId()),
                    occurredAt));
        }
    }

    private byte[] hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("감사 payload HMAC을 계산할 수 없다", exception);
        }
    }

    private static String canonical(AuditRecordCommand command, AuditContext context) {
        List<String> values = new ArrayList<>();
        values.add(command.domain());
        values.add(command.action());
        values.add(command.operationId());
        values.add(Integer.toString(command.eventSequence()));
        values.add(command.primaryTarget().targetType().name());
        values.add(command.primaryTarget().targetId());
        values.add(Integer.toString(command.relatedTargets().size()));
        for (AuditRelatedTarget target : command.relatedTargets()) {
            values.add(target.relationType().name());
            values.add(target.targetType().name());
            values.add(target.targetId());
        }
        values.add(command.beforeValue());
        values.add(command.afterValue());
        values.add(command.reasonCode());
        values.add(command.reason());
        values.add(command.valueSchemaVersion());
        values.add(context.actor());
        values.add(context.actorRole());
        values.add(context.occurredAt().toString());
        values.add(context.requestId());
        return String.join("|", values.stream().map(JpaAuditWriter::canonicalPart).toList());
    }

    private static String canonicalPart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private record TargetKey(String relationType, String targetType, String targetId) {}
}
