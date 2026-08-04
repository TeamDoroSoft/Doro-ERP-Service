package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "feature_setting")
class FeatureSettingEntity {

    @EmbeddedId private FeatureSettingId id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected FeatureSettingEntity() {}

    FeatureSettingEntity(FeatureSettingId id, boolean enabled) {
        this.id = id;
        this.enabled = enabled;
    }

    FeatureSettingId getId() {
        return id;
    }

    boolean isEnabled() {
        return enabled;
    }
}
