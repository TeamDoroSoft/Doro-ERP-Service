package com.dorosoft.erp.store.application.port;

import java.util.Optional;

import com.dorosoft.erp.store.domain.settings.StoreSettings;

// 업체 ID·Schema명을 파라미터로 받지 않는다. 배포당 DataSource 1개가 이미 해당 업체 Schema를 가리킨다(SR-STORE-007).
public interface StoreSettingsRepository {

    Optional<StoreSettings> findCurrent();

    StoreSettings save(StoreSettings settings);

    boolean exists();
}
