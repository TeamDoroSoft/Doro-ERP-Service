package com.dorosoft.erp.store.application.availability;

import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.port.StoreAvailabilityReader;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.availability.AvailabilityPolicy;
import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreAvailabilityReaderImpl implements StoreAvailabilityReader {

    private final StoreSettingsRepository repository;
    private final AvailabilityPolicy policy = new AvailabilityPolicy();

    public StoreAvailabilityReaderImpl(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureAvailability evaluate(FeatureCode featureCode, Instant instant) {
        StoreSettings settings = repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
        return policy.evaluate(settings, featureCode, instant);
    }
}
