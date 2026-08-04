package com.dorosoft.erp.store.application.dto;

import com.dorosoft.erp.store.domain.availability.FeatureAvailability;
import com.dorosoft.erp.store.domain.settings.StoreSettings;

public final class PublicStoreAvailabilityWebMapper {

    private PublicStoreAvailabilityWebMapper() {}

    public static PublicStoreAvailabilityResponse toResponse(
            StoreSettings settings, FeatureAvailability availability) {
        return new PublicStoreAvailabilityResponse(
                settings.profile().name(),
                availability.featureCode(),
                availability.available(),
                availability.reason(),
                availability.evaluatedAt(),
                availability.nextAvailableAt());
    }
}
