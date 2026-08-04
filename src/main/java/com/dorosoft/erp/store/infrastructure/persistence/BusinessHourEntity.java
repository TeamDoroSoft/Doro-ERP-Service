package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** store_id는 소유자(StoreProfileEntity)의 @JoinColumn이 채운다. */
@Entity
@Table(name = "business_hour")
class BusinessHourEntity {

    @Id
    @Column(name = "business_hour_id", nullable = false)
    private UUID businessHourId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "period_order", nullable = false)
    private short periodOrder;

    @Column(name = "start_local_time", nullable = false)
    private LocalTime startLocalTime;

    @Column(name = "end_local_time", nullable = false)
    private LocalTime endLocalTime;

    protected BusinessHourEntity() {}

    BusinessHourEntity(
            UUID businessHourId,
            short dayOfWeek,
            short periodOrder,
            LocalTime startLocalTime,
            LocalTime endLocalTime) {
        this.businessHourId = businessHourId;
        this.dayOfWeek = dayOfWeek;
        this.periodOrder = periodOrder;
        this.startLocalTime = startLocalTime;
        this.endLocalTime = endLocalTime;
    }

    short getDayOfWeek() {
        return dayOfWeek;
    }

    short getPeriodOrder() {
        return periodOrder;
    }

    LocalTime getStartLocalTime() {
        return startLocalTime;
    }

    LocalTime getEndLocalTime() {
        return endLocalTime;
    }
}
