package com.dorosoft.erp.store.application.availability;

import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityObserver {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityObserver.class);

    private final MeterRegistry meterRegistry;

    public AvailabilityObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void evaluated(
            FeatureCode featureCode, FeatureAvailability availability, AvailabilityCaller caller) {
        safely(() -> meterRegistry
                .counter(
                        "store.availability.evaluations",
                        "feature",
                        featureCode.name(),
                        "reason",
                        availability.reason().name(),
                        "caller",
                        caller.name().toLowerCase(Locale.ROOT))
                .increment());
    }

    public void failed(FeatureCode featureCode, AvailabilityCaller caller, String cause) {
        safely(() -> meterRegistry
                .counter(
                        "store.availability.failures",
                        "feature",
                        featureCode.name(),
                        "caller",
                        caller.name().toLowerCase(Locale.ROOT),
                        "cause",
                        cause)
                .increment());
    }

    public Timer.Sample startTimer() {
        try {
            return Timer.start(meterRegistry);
        } catch (RuntimeException exception) {
            LOG.warn("store.observability.failed", exception);
            return null;
        }
    }

    public void stopTimer(Timer.Sample sample, AvailabilityCaller caller) {
        safely(() -> {
            if (sample != null) {
                sample.stop(Timer.builder("store.availability.evaluation")
                        .tag("caller", caller.name().toLowerCase(Locale.ROOT))
                        .register(meterRegistry));
            }
        });
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOG.warn("store.observability.failed", exception);
        }
    }
}
