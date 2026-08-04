package com.dorosoft.erp.store.application.availability;

import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.port.StoreAvailabilityReader;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.availability.AvailabilityPolicy;
import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreAvailabilityReaderImpl implements StoreAvailabilityReader {

    private static final Logger log = LoggerFactory.getLogger(StoreAvailabilityReaderImpl.class);

    private final StoreSettingsRepository repository;
    private final AvailabilityObserver observer;
    private final AvailabilityPolicy policy = new AvailabilityPolicy();

    public StoreAvailabilityReaderImpl(
            StoreSettingsRepository repository, AvailabilityObserver observer) {
        this.repository = repository;
        this.observer = observer;
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureAvailability evaluate(FeatureCode featureCode, Instant instant) {
        Timer.Sample sample = observer.startTimer();
        try {
            StoreSettings settings =
                    repository.findCurrent().orElseThrow(StoreNotInitializedException::new);
            FeatureAvailability availability = policy.evaluate(settings, featureCode, instant);
            observer.evaluated(featureCode, availability, AvailabilityCaller.INTERNAL_READER);
            log.debug(
                    "store.availability.evaluated feature={} reason={} available={} evaluatedAt={} nextAvailableAt={}",
                    featureCode,
                    availability.reason(),
                    availability.available(),
                    availability.evaluatedAt(),
                    availability.nextAvailableAt());
            return availability;
        } catch (StoreNotInitializedException exception) {
            observer.failed(featureCode, AvailabilityCaller.INTERNAL_READER, "store_not_initialized");
            log.warn("store.availability.failed feature={} cause=STORE_NOT_INITIALIZED", featureCode);
            throw exception;
        } catch (RuntimeException exception) {
            observer.failed(featureCode, AvailabilityCaller.INTERNAL_READER, "unexpected");
            log.error("store.availability.failed feature={} cause=UNEXPECTED", featureCode, exception);
            throw exception;
        } finally {
            observer.stopTimer(sample, AvailabilityCaller.INTERNAL_READER);
        }
    }
}
