package com.dorosoft.erp.store.application.feature;

import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import java.util.Map;

public record UpdateFeatureSettingsCommand(
        Map<FeatureCode, Boolean> customerFeatures,
        Map<NotificationEventCode, Boolean> notificationEvents,
        long ifMatchVersion) {}
