package com.dorosoft.erp.store.domain.schedule;

/** 운영 일정 불변식 위반을 위반 유형과 함께 전달하는 순수 도메인 예외. */
public final class OperatingScheduleViolationException extends RuntimeException {

    public enum Reason {
        OVERLAPPING_BUSINESS_HOURS,
        SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS,
        CLOSED_DAY_HAS_BUSINESS_HOURS,
        DUPLICATE_TEMPORARY_CLOSURE,
        OVERLAPPING_SERVICE_WINDOW,
        DUPLICATE_PERIOD_ORDER
    }

    private final Reason reason;
    private final String field;

    public OperatingScheduleViolationException(Reason reason, String field, String message) {
        super(message);
        this.reason = reason;
        this.field = field;
    }

    public Reason reason() {
        return reason;
    }

    public String field() {
        return field;
    }
}
