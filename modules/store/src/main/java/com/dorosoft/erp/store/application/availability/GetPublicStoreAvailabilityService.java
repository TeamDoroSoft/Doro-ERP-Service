package com.dorosoft.erp.store.application.availability;

import com.dorosoft.erp.store.application.dto.PublicStoreAvailabilityResponse;
import com.dorosoft.erp.store.application.dto.PublicStoreAvailabilityWebMapper;
import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.availability.AvailabilityPolicy;
import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicStoreAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(GetPublicStoreAvailabilityService.class);

    private final StoreSettingsRepository repository;
    private final Clock clock;
    private final AvailabilityObserver observer;
    private final AvailabilityPolicy policy = new AvailabilityPolicy();

    public GetPublicStoreAvailabilityService(
            StoreSettingsRepository repository, Clock clock, AvailabilityObserver observer) {
        this.repository = repository;
        this.clock = clock;
        this.observer = observer;
    }

    @Transactional(readOnly = true)
    public PublicStoreAvailabilityResponse get(FeatureCode featureCode) {
        Timer.Sample sample = observer.startTimer();
        try {
            StoreSettings settings =
                    repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
            Instant evaluatedAt = clock.instant();
            FeatureAvailability availability = policy.evaluate(settings, featureCode, evaluatedAt);
            observer.evaluated(featureCode, availability, AvailabilityCaller.PUBLIC_API);
            log.debug(
                    "store.availability.evaluated feature={} reason={} available={} evaluatedAt={} nextAvailableAt={}",
                    featureCode,
                    availability.reason(),
                    availability.available(),
                    availability.evaluatedAt(),
                    availability.nextAvailableAt());
            return PublicStoreAvailabilityWebMapper.toResponse(settings, availability);
        } catch (StoreNotInitializedException exception) {
            observer.failed(featureCode, AvailabilityCaller.PUBLIC_API, "store_not_initialized");
            log.warn("store.availability.failed feature={} cause=STORE_NOT_INITIALIZED", featureCode);
            throw exception;
        } catch (RuntimeException exception) {
            observer.failed(featureCode, AvailabilityCaller.PUBLIC_API, "unexpected");
            log.error("store.availability.failed feature={} cause=UNEXPECTED", featureCode, exception);
            throw exception;
        } finally {
            observer.stopTimer(sample, AvailabilityCaller.PUBLIC_API);
        }
    }
}
