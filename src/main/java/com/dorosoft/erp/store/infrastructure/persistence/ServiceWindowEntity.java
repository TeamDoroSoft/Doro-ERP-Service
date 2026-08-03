package com.dorosoft.erp.store.infrastructure.persistence;

import com.dorosoft.erp.store.domain.schedule.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "service_window")
class ServiceWindowEntity {

    @Id
    @Column(name = "service_window_id", nullable = false)
    private UUID serviceWindowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "period_order", nullable = false)
    private short periodOrder;

    @Column(name = "start_local_time", nullable = false)
    private LocalTime startLocalTime;

    @Column(name = "end_local_time", nullable = false)
    private LocalTime endLocalTime;

    protected ServiceWindowEntity() {}

    ServiceWindowEntity(
            UUID serviceWindowId,
            ServiceType serviceType,
            short dayOfWeek,
            short periodOrder,
            LocalTime startLocalTime,
            LocalTime endLocalTime) {
        this.serviceWindowId = serviceWindowId;
        this.serviceType = serviceType;
        this.dayOfWeek = dayOfWeek;
        this.periodOrder = periodOrder;
        this.startLocalTime = startLocalTime;
        this.endLocalTime = endLocalTime;
    }

    ServiceType getServiceType() {
        return serviceType;
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
