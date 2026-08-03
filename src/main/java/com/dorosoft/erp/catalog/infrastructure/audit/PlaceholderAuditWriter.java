package com.dorosoft.erp.catalog.infrastructure.audit;

import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriteResult;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriteStatus;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 5.17 중앙 Audit 모듈(기능 17)이 이 저장소에 아직 구현되지 않아 두는 임시 대체 Bean이다.
 * Catalog는 자체 감사 Table을 두지 않는다는 원칙(03 TBD 종결안)을 지키기 위해 어디에도
 * 영속화하지 않고 확인 응답만 만든다. 기능 17 구현 이후 진짜 AuditWriter Bean으로
 * 교체해야 하며, 그때까지 Catalog의 실제 변경 이력은 보존되지 않는다.
 */
@Component
public class PlaceholderAuditWriter implements AuditWriter {

    @Override
    public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
        return new AuditWriteResult(AuditWriteStatus.RECORDED, UUID.randomUUID().toString(), context.occurredAt());
    }
}
