package com.dorosoft.erp.store.application.audit;

import com.dorosoft.erp.audit.application.api.AuditRelatedTarget;
import com.dorosoft.erp.audit.application.api.AuditRelationType;
import com.dorosoft.erp.audit.application.api.AuditTarget;
import com.dorosoft.erp.audit.application.api.AuditTargetType;
import java.util.List;
import java.util.UUID;

public final class StoreAuditTargets {

    private StoreAuditTargets() {}

    public static AuditTarget primary(AuditTargetType type, UUID storeId) {
        return new AuditTarget(type, storeId.toString());
    }

    public static List<AuditRelatedTarget> related(UUID storeId) {
        return List.of(new AuditRelatedTarget(
                AuditRelationType.STORE, AuditTargetType.STORE_PROFILE, storeId.toString()));
    }
}
