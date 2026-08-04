package com.dorosoft.erp.store.domain.schedule;

import java.time.LocalDate;
import java.util.Objects;

/** 특정 날짜 하루의 임시 휴무. reason은 선택 값이다. */
public record TemporaryClosure(LocalDate date, String reason) {

    public TemporaryClosure {
        Objects.requireNonNull(date, "date는 null일 수 없습니다");
    }
}
