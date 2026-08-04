package com.dorosoft.erp.store.domain.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** 특정 요일의 영업 구간. 요일은 소유 맵의 키가 보유한다. */
public record BusinessPeriod(int order, LocalTime start, LocalTime end) {

    public BusinessPeriod {
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

    /** 종료 시각이 시작 시각보다 이르면 자정을 넘긴 구간이다. */
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
