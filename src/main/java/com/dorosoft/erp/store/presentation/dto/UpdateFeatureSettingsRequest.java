package com.dorosoft.erp.store.presentation.dto;

import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.store.application.exception.InvalidSettingCodeException;
import com.dorosoft.erp.store.application.feature.UpdateFeatureSettingsCommand;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record UpdateFeatureSettingsRequest(
        Map<String, Boolean> customerFeatures, Map<String, Boolean> notificationEvents) {

    public UpdateFeatureSettingsCommand toCommand(long ifMatchVersion) {
        List<FieldError> errors = new ArrayList<>();
        Map<FeatureCode, Boolean> parsedFeatures = parseCustomerFeatures(errors);
        Map<NotificationEventCode, Boolean> parsedEvents = parseNotificationEvents(errors);
        if (!errors.isEmpty()) {
            throw new InvalidSettingCodeException("지원하지 않거나 누락된 설정 코드가 있습니다", errors);
        }
        return new UpdateFeatureSettingsCommand(parsedFeatures, parsedEvents, ifMatchVersion);
    }

    private Map<FeatureCode, Boolean> parseCustomerFeatures(List<FieldError> errors) {
        Map<FeatureCode, Boolean> result = new EnumMap<>(FeatureCode.class);
        if (customerFeatures == null) {
            errors.add(new FieldError("customerFeatures", "REQUIRED"));
            return result;
        }
        customerFeatures.forEach((value, enabled) -> {
            String field = "customerFeatures." + value;
            try {
                FeatureCode code = FeatureCode.valueOf(value);
                if (enabled == null) {
                    errors.add(new FieldError(field, "REQUIRED"));
                } else {
                    result.put(code, enabled);
                }
            } catch (IllegalArgumentException | NullPointerException exception) {
                errors.add(new FieldError(field, "INVALID_SETTING_CODE"));
            }
        });
        return result;
    }

    private Map<NotificationEventCode, Boolean> parseNotificationEvents(List<FieldError> errors) {
        Map<NotificationEventCode, Boolean> result = new EnumMap<>(NotificationEventCode.class);
        if (notificationEvents == null) {
            errors.add(new FieldError("notificationEvents", "REQUIRED"));
            return result;
        }
        notificationEvents.forEach((value, enabled) -> {
            String field = "notificationEvents." + value;
            try {
                NotificationEventCode code = NotificationEventCode.valueOf(value);
                if (enabled == null) {
                    errors.add(new FieldError(field, "REQUIRED"));
                } else {
                    result.put(code, enabled);
                }
            } catch (IllegalArgumentException | NullPointerException exception) {
                errors.add(new FieldError(field, "INVALID_SETTING_CODE"));
            }
        });
        return result;
    }
}
