package com.dorosoft.erp.store.infrastructure.persistence;

import com.dorosoft.erp.store.domain.feature.FeatureCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class FeatureSettingId implements Serializable {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", nullable = false, length = 40)
    private FeatureCode featureCode;

    protected FeatureSettingId() {}

    FeatureSettingId(UUID storeId, FeatureCode featureCode) {
        this.storeId = storeId;
        this.featureCode = featureCode;
    }

    FeatureCode getFeatureCode() {
        return featureCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FeatureSettingId that)) {
            return false;
        }
        return featureCode == that.featureCode && Objects.equals(storeId, that.storeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, featureCode);
    }
}
