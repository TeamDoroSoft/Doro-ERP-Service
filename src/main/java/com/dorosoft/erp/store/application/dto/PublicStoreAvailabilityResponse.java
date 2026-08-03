package com.dorosoft.erp.store.application.dto;

import com.dorosoft.erp.store.domain.availability.AvailabilityReason;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import java.time.Instant;

public record PublicStoreAvailabilityResponse(
        String storeName,
        FeatureCode featureCode,
        boolean available,
        AvailabilityReason reason,
        Instant evaluatedAt,
        Instant nextAvailableAt) {}
