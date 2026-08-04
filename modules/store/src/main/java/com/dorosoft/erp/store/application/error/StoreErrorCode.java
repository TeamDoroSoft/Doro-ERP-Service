package com.dorosoft.erp.store.application.error;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Stable Feature 02 API error catalogue. */
public enum StoreErrorCode implements ProblemCode {
    CLOSED_DAY_HAS_BUSINESS_HOURS(
            "CLOSED_DAY_HAS_BUSINESS_HOURS", HttpStatus.BAD_REQUEST, "정기 휴무 요일에 영업 또는 서비스 구간이 존재합니다."),
    DUPLICATE_PERIOD_ORDER(
            "DUPLICATE_PERIOD_ORDER", HttpStatus.BAD_REQUEST, "order 값이 중복됩니다."),
    DUPLICATE_TEMPORARY_CLOSURE(
            "DUPLICATE_TEMPORARY_CLOSURE", HttpStatus.BAD_REQUEST, "임시 휴무 날짜가 중복됩니다."),
    INVALID_SETTING_CODE(
            "INVALID_SETTING_CODE", HttpStatus.BAD_REQUEST, "설정 코드가 유효하지 않습니다."),
    OVERLAPPING_BUSINESS_HOURS(
            "OVERLAPPING_BUSINESS_HOURS", HttpStatus.BAD_REQUEST, "영업 구간이 서로 겹칩니다."),
    OVERLAPPING_SERVICE_WINDOW(
            "OVERLAPPING_SERVICE_WINDOW", HttpStatus.BAD_REQUEST, "서비스 구간이 서로 겹칩니다."),
    SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS(
            "SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS", HttpStatus.BAD_REQUEST, "서비스 구간이 영업시간을 벗어납니다."),
    STORE_NOT_INITIALIZED(
            "STORE_NOT_INITIALIZED", HttpStatus.NOT_FOUND, "매장 설정이 초기화되지 않았습니다"),
    VERSION_CONFLICT(
            "VERSION_CONFLICT", HttpStatus.CONFLICT, "매장 설정 버전이 일치하지 않습니다");

    private final String title;
    private final HttpStatus status;
    private final String defaultDetail;

    StoreErrorCode(String title, HttpStatus status, String defaultDetail) {
        this.title = title;
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }
}
