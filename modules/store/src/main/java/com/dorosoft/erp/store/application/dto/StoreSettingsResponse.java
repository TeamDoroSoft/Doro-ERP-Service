package com.dorosoft.erp.store.application.dto;

public record StoreSettingsResponse(
        StoreProfileResponse profile,
        StoreScheduleResponse schedule,
        StoreFeatureSettingsResponse features,
        long version) {}
