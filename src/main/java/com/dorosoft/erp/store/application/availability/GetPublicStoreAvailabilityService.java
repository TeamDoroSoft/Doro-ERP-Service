package com.dorosoft.erp.store.application.availability;

import com.dorosoft.erp.store.application.dto.PublicStoreAvailabilityResponse;
import com.dorosoft.erp.store.application.dto.PublicStoreAvailabilityWebMapper;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.availability.AvailabilityPolicy;
import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicStoreAvailabilityService {

    private final StoreSettingsRepository repository;
    private final Clock clock;
    private final AvailabilityPolicy policy = new AvailabilityPolicy();

    public GetPublicStoreAvailabilityService(StoreSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicStoreAvailabilityResponse get(FeatureCode featureCode) {
        StoreSettings settings = repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
        Instant evaluatedAt = clock.instant();
        FeatureAvailability availability = policy.evaluate(settings, featureCode, evaluatedAt);
        return PublicStoreAvailabilityWebMapper.toResponse(settings, availability);
    }
}
