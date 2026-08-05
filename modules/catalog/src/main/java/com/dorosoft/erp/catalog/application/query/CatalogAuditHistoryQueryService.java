package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditTargetType;
import com.dorosoft.erp.catalog.infrastructure.audit.CatalogAuditTenantProperties;
import org.springframework.stereotype.Service;

/**
 * Catalog 변경 이력 조회(GET /catalog/history, API 명세). 5.17 {@code AuditQuery}를
 * {@code domain=CATALOG}로 고정해 호출하는 호환 Endpoint일 뿐, Catalog는 자체 감사 Table이나
 * Repository를 두지 않는다.
 */
@Service
public class CatalogAuditHistoryQueryService {

    private final AuditQuery auditQuery;
    private final CatalogAuditTenantProperties tenantProperties;

    public CatalogAuditHistoryQueryService(AuditQuery auditQuery, CatalogAuditTenantProperties tenantProperties) {
        this.auditQuery = auditQuery;
        this.tenantProperties = tenantProperties;
    }

    public CatalogHistoryPage query(CatalogHistoryFilter filter) {
        var result =
                auditQuery.query(
                        tenantProperties.tenantId(),
                        new AuditQueryFilter(
                                AuditDomain.CATALOG,
                                filter.action(),
                                filter.actorId(),
                                parseTargetType(filter.targetType()),
                                filter.targetId(),
                                filter.from(),
                                filter.to(),
                                filter.limit(),
                                filter.cursor()));

        return new CatalogHistoryPage(result.items().stream().map(CatalogAuditHistoryQueryService::toEntry).toList(), result.nextCursor());
    }

    private static AuditTargetType parseTargetType(String targetType) {
        if (targetType == null) {
            return null;
        }
        try {
            return AuditTargetType.valueOf(targetType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static CatalogHistoryEntry toEntry(AuditRecordProjection projection) {
        return new CatalogHistoryEntry(
                projection.auditId(),
                projection.action(),
                projection.actorType(),
                projection.actorId(),
                projection.actorRoleSnapshot(),
                projection.primaryTargetType() == null ? null : projection.primaryTargetType().name(),
                projection.primaryTargetId(),
                projection.occurredAt(),
                projection.requestId(),
                projection.beforeValue(),
                projection.afterValue());
    }
}
