package com.dorosoft.erp.store.domain.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 고객 기능·알림 이벤트 on/off 설정. 모든 코드가 명시적으로 채워져야 한다. */
public final class FeatureSettings {

    private final Map<FeatureCode, Boolean> customerFeatures;
    private final Map<NotificationEventCode, Boolean> notificationEvents;

    private FeatureSettings(
            Map<FeatureCode, Boolean> customerFeatures,
            Map<NotificationEventCode, Boolean> notificationEvents) {
        this.customerFeatures = customerFeatures;
        this.notificationEvents = notificationEvents;
    }

    public static FeatureSettings of(
            Map<FeatureCode, Boolean> customerFeatures,
            Map<NotificationEventCode, Boolean> notificationEvents) {
        Objects.requireNonNull(customerFeatures, "customerFeatures는 null일 수 없습니다");
        Objects.requireNonNull(notificationEvents, "notificationEvents는 null일 수 없습니다");

        Map<FeatureCode, Boolean> copiedFeatures =
                copyComplete(customerFeatures, FeatureCode.values(), FeatureCode.class, "customerFeatures");
        Map<NotificationEventCode, Boolean> copiedEvents =
                copyComplete(
                        notificationEvents,
                        NotificationEventCode.values(),
                        NotificationEventCode.class,
                        "notificationEvents");

        return new FeatureSettings(
                Collections.unmodifiableMap(copiedFeatures), Collections.unmodifiableMap(copiedEvents));
    }

    public static FeatureSettings allDisabled() {
        Map<FeatureCode, Boolean> features = new EnumMap<>(FeatureCode.class);
        for (FeatureCode code : FeatureCode.values()) {
            features.put(code, Boolean.FALSE);
        }
        Map<NotificationEventCode, Boolean> events = new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventCode code : NotificationEventCode.values()) {
            events.put(code, Boolean.FALSE);
        }
        return of(features, events);
    }

    public Map<FeatureCode, Boolean> customerFeatures() {
        return customerFeatures;
    }

    public Map<NotificationEventCode, Boolean> notificationEvents() {
        return notificationEvents;
    }

    /** 값이 없으면 비활성으로 본다. 암묵적 활성화는 하지 않는다. */
    public boolean isEnabled(FeatureCode featureCode) {
        return Boolean.TRUE.equals(customerFeatures.get(featureCode));
    }

    public boolean isNotificationEnabled(NotificationEventCode eventCode) {
        return Boolean.TRUE.equals(notificationEvents.get(eventCode));
    }

    private static <E extends Enum<E>> Map<E, Boolean> copyComplete(
            Map<E, Boolean> source, E[] allCodes, Class<E> type, String fieldName) {
        Map<E, Boolean> copied = new EnumMap<>(type);
        List<E> missing = new ArrayList<>();
        for (E code : allCodes) {
            Boolean value = source.get(code);
            if (value == null) {
                missing.add(code);
            } else {
                copied.put(code, value);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "에 누락되었거나 값이 null인 코드가 있습니다: " + missing);
        }
        return copied;
    }
}
