package com.dorosoft.erp.store.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FeatureSettings: 모든 기능·알림 코드가 명시적으로 채워져야 한다")
class FeatureSettingsTest {

    private static Map<FeatureCode, Boolean> allFeatures(boolean value) {
        Map<FeatureCode, Boolean> map = new HashMap<>();
        for (FeatureCode code : FeatureCode.values()) {
            map.put(code, value);
        }
        return map;
    }

    private static Map<NotificationEventCode, Boolean> allEvents(boolean value) {
        Map<NotificationEventCode, Boolean> map = new HashMap<>();
        for (NotificationEventCode code : NotificationEventCode.values()) {
            map.put(code, value);
        }
        return map;
    }

    @Test
    @DisplayName("FeatureCode가 하나라도 빠지면 거부하고 메시지에 빠진 코드명을 담는다")
    void rejectsMissingFeatureCode() {
        Map<FeatureCode, Boolean> features = allFeatures(true);
        features.remove(FeatureCode.QR_ORDER);

        assertThatThrownBy(() -> FeatureSettings.of(features, allEvents(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QR_ORDER");
    }

    @Test
    @DisplayName("NotificationEventCode가 하나라도 빠지면 거부하고 메시지에 빠진 코드명을 담는다")
    void rejectsMissingNotificationEventCode() {
        Map<NotificationEventCode, Boolean> events = allEvents(true);
        events.remove(NotificationEventCode.PAYMENT_CANCELLED);

        assertThatThrownBy(() -> FeatureSettings.of(allFeatures(true), events))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYMENT_CANCELLED");
    }

    @Test
    @DisplayName("키는 있으나 값이 null이면 누락으로 보고 거부한다")
    void rejectsNullValue() {
        Map<FeatureCode, Boolean> features = allFeatures(true);
        features.put(FeatureCode.WAITING, null);

        assertThatThrownBy(() -> FeatureSettings.of(features, allEvents(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WAITING");
    }

    @Test
    @DisplayName("customerFeatures·notificationEvents가 null이면 NullPointerException")
    void rejectsNullMaps() {
        assertThatThrownBy(() -> FeatureSettings.of(null, allEvents(true)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FeatureSettings.of(allFeatures(true), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isEnabled·isNotificationEnabled는 명시된 값을 그대로 돌려준다")
    void returnsExplicitValues() {
        Map<FeatureCode, Boolean> features = allFeatures(false);
        features.put(FeatureCode.WAITING, true);
        Map<NotificationEventCode, Boolean> events = allEvents(false);
        events.put(NotificationEventCode.WAITING_CALLED, true);

        FeatureSettings settings = FeatureSettings.of(features, events);

        assertThat(settings.isEnabled(FeatureCode.WAITING)).isTrue();
        assertThat(settings.isEnabled(FeatureCode.RESERVATION)).isFalse();
        assertThat(settings.isNotificationEnabled(NotificationEventCode.WAITING_CALLED)).isTrue();
        assertThat(settings.isNotificationEnabled(NotificationEventCode.WAITING_REGISTERED)).isFalse();
    }

    @Test
    @DisplayName("allDisabled()는 모든 기능·알림을 비활성으로 채운다")
    void allDisabledTurnsEverythingOff() {
        FeatureSettings settings = FeatureSettings.allDisabled();

        for (FeatureCode code : FeatureCode.values()) {
            assertThat(settings.isEnabled(code)).as("기능 %s", code).isFalse();
        }
        for (NotificationEventCode code : NotificationEventCode.values()) {
            assertThat(settings.isNotificationEnabled(code)).as("알림 %s", code).isFalse();
        }
    }

    @Test
    @DisplayName("노출된 맵은 수정할 수 없다")
    void exposedMapsAreUnmodifiable() {
        FeatureSettings settings = FeatureSettings.allDisabled();

        assertThatThrownBy(() -> settings.customerFeatures().put(FeatureCode.WAITING, true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () ->
                                settings
                                        .notificationEvents()
                                        .put(NotificationEventCode.WAITING_CALLED, true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("of()에 넘긴 맵을 나중에 바꿔도 FeatureSettings는 영향을 받지 않는다")
    void defensivelyCopiesSourceMaps() {
        Map<FeatureCode, Boolean> features = allFeatures(false);
        Map<NotificationEventCode, Boolean> events = allEvents(false);
        FeatureSettings settings = FeatureSettings.of(features, events);

        features.put(FeatureCode.WAITING, true);
        events.put(NotificationEventCode.WAITING_CALLED, true);

        assertThat(settings.isEnabled(FeatureCode.WAITING)).isFalse();
        assertThat(settings.isNotificationEnabled(NotificationEventCode.WAITING_CALLED)).isFalse();
    }

    @Test
    @DisplayName("보유한 코드 집합은 enum values() 전체와 일치한다 (기능 4개, 알림 13개)")
    void coversAllEnumValues() {
        FeatureSettings settings = FeatureSettings.allDisabled();

        assertThat(settings.customerFeatures()).hasSize(4);
        assertThat(settings.notificationEvents()).hasSize(13);
        assertThat(settings.customerFeatures().keySet())
                .isEqualTo(EnumSet.allOf(FeatureCode.class));
        assertThat(settings.notificationEvents().keySet())
                .isEqualTo(EnumSet.allOf(NotificationEventCode.class));
    }
}
