package com.dorosoft.erp.store.domain.settings;

import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import java.util.Objects;
import java.util.UUID;

/** 매장 설정 Aggregate Root. */
public final class StoreSettings {

    private final UUID storeId;
    private StoreProfile profile;
    private OperatingSchedule schedule;
    private FeatureSettings features;
    private long version;

    private StoreSettings(
            UUID storeId,
            StoreProfile profile,
            OperatingSchedule schedule,
            FeatureSettings features,
            long version) {
        this.storeId = Objects.requireNonNull(storeId, "storeId는 null일 수 없습니다");
        this.profile = Objects.requireNonNull(profile, "profile은 null일 수 없습니다");
        this.schedule = Objects.requireNonNull(schedule, "schedule은 null일 수 없습니다");
        this.features = Objects.requireNonNull(features, "features는 null일 수 없습니다");
        if (version < 0) {
            throw new IllegalArgumentException("version은 0 이상이어야 합니다: " + version);
        }
        this.version = version;
    }

    public static StoreSettings create(
            UUID storeId,
            StoreProfile profile,
            OperatingSchedule schedule,
            FeatureSettings features) {
        StoreSettings settings = new StoreSettings(storeId, profile, schedule, features, 0L);
        settings.schedule.validate();
        return settings;
    }

    public static StoreSettings reconstitute(
            UUID storeId,
            StoreProfile profile,
            OperatingSchedule schedule,
            FeatureSettings features,
            long version) {
        StoreSettings settings = new StoreSettings(storeId, profile, schedule, features, version);
        settings.schedule.validate();
        return settings;
    }

    public UUID storeId() {
        return storeId;
    }

    public StoreProfile profile() {
        return profile;
    }

    public OperatingSchedule schedule() {
        return schedule;
    }

    public FeatureSettings features() {
        return features;
    }

    public long version() {
        return version;
    }

    public void updateProfile(StoreProfile newProfile) {
        this.profile = Objects.requireNonNull(newProfile, "newProfile은 null일 수 없습니다");
    }

    public void replaceSchedule(OperatingSchedule newSchedule) {
        Objects.requireNonNull(newSchedule, "newSchedule은 null일 수 없습니다");
        newSchedule.validate();
        this.schedule = newSchedule;
    }

    public void replaceFeatures(FeatureSettings newFeatures) {
        this.features = Objects.requireNonNull(newFeatures, "newFeatures는 null일 수 없습니다");
    }
}
