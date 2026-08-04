package com.dorosoft.erp.store.application.audit;

import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import java.util.Map;
import java.util.UUID;

public final class StoreAuditTargets {

    private StoreAuditTargets() {}

    public static AuditRecordCommand storeProfileUpdated(
            UUID operationId, UUID storeId, Map<String, Object> beforeValue, Map<String, Object> afterValue) {
        return AuditRecordCommand.storeProfileUpdated(operationId, storeId, beforeValue, afterValue);
    }

    public static AuditRecordCommand storeScheduleUpdated(
            UUID operationId, UUID storeId, Map<String, Object> beforeValue, Map<String, Object> afterValue) {
        return AuditRecordCommand.storeScheduleUpdated(operationId, storeId, beforeValue, afterValue);
    }

    public static AuditRecordCommand storeFeatureSettingsUpdated(
            UUID operationId, UUID storeId, Map<String, Object> beforeValue, Map<String, Object> afterValue) {
        return AuditRecordCommand.storeFeatureSettingsUpdated(operationId, storeId, beforeValue, afterValue);
    }
}
