package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class RegularClosedDayId implements Serializable {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    protected RegularClosedDayId() {}

    RegularClosedDayId(UUID storeId, short dayOfWeek) {
        this.storeId = storeId;
        this.dayOfWeek = dayOfWeek;
    }

    short getDayOfWeek() {
        return dayOfWeek;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegularClosedDayId that)) {
            return false;
        }
        return dayOfWeek == that.dayOfWeek && Objects.equals(storeId, that.storeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, dayOfWeek);
    }
}
