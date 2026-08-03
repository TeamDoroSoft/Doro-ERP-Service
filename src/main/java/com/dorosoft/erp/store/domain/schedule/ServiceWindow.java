package com.dorosoft.erp.store.domain.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** 서비스(주문·예약)를 받을 수 있는 요일별 구간. */
public record ServiceWindow(
        ServiceType serviceType, DayOfWeek dayOfWeek, int order, LocalTime start, LocalTime end) {

    public ServiceWindow {
        Objects.requireNonNull(serviceType, "serviceType은 null일 수 없습니다");
        Objects.requireNonNull(dayOfWeek, "dayOfWeek는 null일 수 없습니다");
        Objects.requireNonNull(start, "start는 null일 수 없습니다");
        Objects.requireNonNull(end, "end는 null일 수 없습니다");
        if (order < 0) {
            throw new IllegalArgumentException("order는 0 이상이어야 합니다: " + order);
        }
        if (start.equals(end)) {
            throw new IllegalArgumentException(
                    "start와 end가 같으면 길이가 0이거나 24시간인지 구분할 수 없습니다: " + start);
        }
    }

    public boolean crossesMidnight() {
        return end.isBefore(start);
    }

    public TimeInterval toInterval(LocalDate startDate, ZoneId zone) {
        Objects.requireNonNull(startDate, "startDate는 null일 수 없습니다");
        Objects.requireNonNull(zone, "zone은 null일 수 없습니다");
        LocalDate endDate = crossesMidnight() ? startDate.plusDays(1) : startDate;
        Instant startInstant = ZonedDateTime.of(startDate, start, zone).toInstant();
        Instant endInstant = ZonedDateTime.of(endDate, end, zone).toInstant();
        return new TimeInterval(startInstant, endInstant);
    }
}
