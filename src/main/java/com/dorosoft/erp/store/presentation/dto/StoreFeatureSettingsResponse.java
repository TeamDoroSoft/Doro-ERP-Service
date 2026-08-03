package com.dorosoft.erp.store.presentation.dto;

import java.util.Map;

public record StoreFeatureSettingsResponse(
        Map<String, Boolean> customerFeatures, Map<String, Boolean> notificationEvents) {}
