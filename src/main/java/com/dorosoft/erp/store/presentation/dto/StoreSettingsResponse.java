package com.dorosoft.erp.store.presentation.dto;

public record StoreSettingsResponse(
        StoreProfileResponse profile,
        StoreScheduleResponse schedule,
        StoreFeatureSettingsResponse features,
        long version) {}
