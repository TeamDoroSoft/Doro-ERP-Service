package com.dorosoft.erp.store.domain.availability;

import com.dorosoft.erp.store.domain.feature.FeatureCode;
import java.time.Instant;
import java.util.Objects;

public record FeatureAvailability(
        FeatureCode featureCode,
        boolean available,
        AvailabilityReason reason,
        Instant evaluatedAt,
        Instant nextAvailableAt) {

    public FeatureAvailability {
        Objects.requireNonNull(featureCode, "featureCode는 null일 수 없습니다");
        Objects.requireNonNull(reason, "reason은 null일 수 없습니다");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt은 null일 수 없습니다");
        if (available != (reason == AvailabilityReason.AVAILABLE)) {
            throw new IllegalArgumentException("available은 reason이 AVAILABLE일 때만 true여야 합니다");
        }
        if (available && nextAvailableAt != null) {
            throw new IllegalArgumentException("이미 이용 가능하면 nextAvailableAt은 null이어야 합니다");
        }
        if (reason == AvailabilityReason.FEATURE_DISABLED && nextAvailableAt != null) {
            throw new IllegalArgumentException("기능이 비활성화되면 nextAvailableAt은 null이어야 합니다");
        }
    }
}
