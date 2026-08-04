package com.dorosoft.erp.platform.web;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode implements ProblemCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증 실패"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류");

    private final HttpStatus status;
    private final String title;

    ApiErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
