package com.dorosoft.erp.store.application.audit;

import java.util.List;

public record StoreProfileAuditValue(
        String storeName, String timeZone, List<String> changedFields, long version) {}
