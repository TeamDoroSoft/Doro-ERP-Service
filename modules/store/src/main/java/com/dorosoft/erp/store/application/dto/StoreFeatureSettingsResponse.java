package com.dorosoft.erp.store.application.dto;

import java.util.Map;

public record StoreFeatureSettingsResponse(
        Map<String, Boolean> customerFeatures, Map<String, Boolean> notificationEvents) {}
