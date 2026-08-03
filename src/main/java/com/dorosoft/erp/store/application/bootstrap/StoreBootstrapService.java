package com.dorosoft.erp.store.application.bootstrap;

import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배포 직후 매장 설정 Aggregate가 없으면 초기값으로 한 번 생성한다. */
@Service
public class StoreBootstrapService {

    private static final String UNSET = "미설정";
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Asia/Seoul");

    private final StoreSettingsRepository repository;

    public StoreBootstrapService(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StoreSettings bootstrap() {
        return repository.findCurrent().orElseGet(this::createInitialSettings);
    }

    private StoreSettings createInitialSettings() {
        StoreSettings initial =
                StoreSettings.create(
                        UUID.randomUUID(),
                        new StoreProfile(UNSET, UNSET, UNSET, DEFAULT_TIME_ZONE),
                        OperatingSchedule.empty(),
                        FeatureSettings.of(defaultCustomerFeatures(), defaultNotificationEvents()));
        return repository.save(initial);
    }

    /** 승인된 고객 기능 기본값. 누락값을 암묵적으로 활성화하지 않도록 4개 코드를 모두 명시한다. */
    private static Map<FeatureCode, Boolean> defaultCustomerFeatures() {
        Map<FeatureCode, Boolean> features = new EnumMap<>(FeatureCode.class);
        features.put(FeatureCode.WAITING, Boolean.TRUE);
        features.put(FeatureCode.RESERVATION, Boolean.TRUE);
        features.put(FeatureCode.QR_ORDER, Boolean.TRUE);
        features.put(FeatureCode.PICKUP_ORDER, Boolean.FALSE);
        return features;
    }

    /** 알림 이벤트는 발신 프로필·Template 승인 전까지 13종 모두 비활성이다. */
    private static Map<NotificationEventCode, Boolean> defaultNotificationEvents() {
        Map<NotificationEventCode, Boolean> events = new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventCode code : NotificationEventCode.values()) {
            events.put(code, Boolean.FALSE);
        }
        return events;
    }
}
