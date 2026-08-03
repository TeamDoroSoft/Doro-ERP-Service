package com.dorosoft.erp.store.domain.schedule;

import java.time.Instant;
import java.util.Objects;

/** 시작 포함, 종료 제외인 시각 구간 [start, end). */
public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        Objects.requireNonNull(start, "start는 null일 수 없습니다");
        Objects.requireNonNull(end, "end는 null일 수 없습니다");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end는 start보다 뒤여야 합니다: start=" + start + ", end=" + end);
        }
    }

    public boolean contains(Instant at) {
        Objects.requireNonNull(at, "at은 null일 수 없습니다");
        return !at.isBefore(start) && at.isBefore(end);
    }
}
