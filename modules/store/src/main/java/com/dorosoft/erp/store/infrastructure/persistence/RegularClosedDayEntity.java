package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "regular_closed_day")
class RegularClosedDayEntity {

    @EmbeddedId private RegularClosedDayId id;

    protected RegularClosedDayEntity() {}

    RegularClosedDayEntity(RegularClosedDayId id) {
        this.id = id;
    }

    RegularClosedDayId getId() {
        return id;
    }
}
