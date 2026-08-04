package com.dorosoft.erp.store.application.audit;

import java.util.Map;

public record StoreFeatureSettingsAuditValue(
        Map<String, Boolean> featureSettings,
        Map<String, Boolean> notificationEventSettings,
        long version) {}
