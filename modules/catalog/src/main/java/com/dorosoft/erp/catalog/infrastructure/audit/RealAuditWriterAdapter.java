package com.dorosoft.erp.catalog.infrastructure.audit;

import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriteResult;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriteStatus;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Catalog의 감사 기록 계약을 5.17 중앙 Audit 모듈(기능 17)의 실제 계약으로 번역하는 Adapter다.
 * 두 모듈은 감사 대상·관계 Enum 값과 CATEGORY_CREATED 등 Action별 허용 필드가 이미 서로 맞춰
 * 설계되어 있어({@code AuditActionSchemaRegistry}), 여기서는 형(Type) 변환만 한다. 배포당 하나의
 * 업체만 서비스하므로(ADR-007과 같은 전제) tenantId는 요청이 아니라 배포 설정값에서 가져온다.
 * Catalog 쪽 actor는 표시용 문자열뿐이라, 실제 계약이 요구하는 actorId는 그 문자열에서 결정적으로
 * 파생한 UUID를 쓴다(같은 actor 문자열은 항상 같은 UUID가 된다).
 */
@Component
public class RealAuditWriterAdapter implements AuditWriter {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final com.dorosoft.erp.audit.application.api.AuditWriter delegate;
    private final CatalogAuditTenantProperties tenantProperties;
    private final ObjectMapper objectMapper;

    public RealAuditWriterAdapter(
            com.dorosoft.erp.audit.application.api.AuditWriter delegate,
            CatalogAuditTenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.tenantProperties = tenantProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
        var realContext =
                com.dorosoft.erp.audit.application.api.AuditContext.identityUser(
                        tenantProperties.tenantId(),
                        actorIdFrom(context.actor()),
                        context.actorRole(),
                        context.actor(),
                        context.requestId(),
                        context.occurredAt());

        var realCommand =
                new com.dorosoft.erp.audit.application.api.AuditRecordCommand(
                        com.dorosoft.erp.audit.domain.AuditDomain.valueOf(command.domain()),
                        com.dorosoft.erp.audit.domain.AuditAction.valueOf(command.action()),
                        UUID.fromString(command.operationId()),
                        command.eventSequence(),
                        toRealPrimaryTarget(command.primaryTarget()),
                        toRealRelatedTargets(command.relatedTargets()),
                        parseJson(command.beforeValue()),
                        parseJson(command.afterValue()),
                        command.reasonCode(),
                        command.reason(),
                        parseSchemaVersion(command.valueSchemaVersion()));

        var realResult = delegate.record(realCommand, realContext);

        return new AuditWriteResult(
                AuditWriteStatus.valueOf(realResult.status().name()),
                realResult.auditId().toString(),
                realResult.occurredAt());
    }

    private static UUID actorIdFrom(String actor) {
        return UUID.nameUUIDFromBytes(actor.getBytes(StandardCharsets.UTF_8));
    }

    private static com.dorosoft.erp.audit.domain.AuditPrimaryTarget toRealPrimaryTarget(
            com.dorosoft.erp.catalog.application.port.audit.AuditTarget target) {
        return new com.dorosoft.erp.audit.domain.AuditPrimaryTarget(
                com.dorosoft.erp.audit.domain.AuditTargetType.valueOf(target.targetType().name()),
                UUID.fromString(target.targetId()));
    }

    private static List<com.dorosoft.erp.audit.domain.AuditRelatedTarget> toRealRelatedTargets(
            List<com.dorosoft.erp.catalog.application.port.audit.AuditRelatedTarget> relatedTargets) {
        return relatedTargets.stream()
                .map(
                        target ->
                                new com.dorosoft.erp.audit.domain.AuditRelatedTarget(
                                        com.dorosoft.erp.audit.domain.AuditRelationType.valueOf(target.relationType().name()),
                                        com.dorosoft.erp.audit.domain.AuditTargetType.valueOf(target.targetType().name()),
                                        UUID.fromString(target.targetId())))
                .toList();
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("감사 값 JSON을 해석할 수 없습니다: " + json, ex);
        }
    }

    private static int parseSchemaVersion(String value) {
        String normalized = value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("지원하지 않는 valueSchemaVersion: " + value, ex);
        }
    }
}
