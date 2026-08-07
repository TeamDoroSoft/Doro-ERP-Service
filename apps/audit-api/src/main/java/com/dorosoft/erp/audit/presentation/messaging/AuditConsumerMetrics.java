package com.dorosoft.erp.audit.presentation.messaging;

import com.dorosoft.erp.audit.application.api.ingest.IngestOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AuditConsumerMetrics {

    private final MeterRegistry meterRegistry;

    public AuditConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void processed(IngestOutcome outcome) {
        meterRegistry.counter(
                        "audit.consumer.processed",
                        "outcome",
                        outcome.name().toLowerCase(Locale.ROOT))
                .increment();
    }

    void failed(FailureCategory category) {
        meterRegistry.counter(
                        "audit.consumer.failed",
                        "category",
                        category.name().toLowerCase(Locale.ROOT))
                .increment();
    }

    enum FailureCategory {
        DESERIALIZATION,
        CONTRACT,
        PERSISTENCE_OR_INTERNAL,
        ACKNOWLEDGEMENT
    }
}
