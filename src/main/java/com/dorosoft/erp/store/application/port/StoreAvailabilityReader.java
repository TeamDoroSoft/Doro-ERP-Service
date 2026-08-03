package com.dorosoft.erp.store.application.port;

import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import java.time.Instant;

public interface StoreAvailabilityReader {

    FeatureAvailability evaluate(FeatureCode featureCode, Instant instant);
}
