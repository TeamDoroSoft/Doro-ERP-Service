package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for local security history queries (ADR-02-015). */
public enum SecurityHistoryProblemCode implements ProblemCode {

    SECURITY_HISTORY_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "보안 이력을 조회할 권한이 없습니다."),
    INVALID_SECURITY_HISTORY_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
    INVALID_SECURITY_HISTORY_PAGE_SIZE(HttpStatus.BAD_REQUEST, "조회 건수가 올바르지 않습니다."),
    INVALID_SECURITY_HISTORY_FILTER(HttpStatus.BAD_REQUEST, "조회 조건이 올바르지 않습니다."),
    INVALID_SECURITY_HISTORY_CURSOR(HttpStatus.BAD_REQUEST, "Cursor 값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String title;

    SecurityHistoryProblemCode(HttpStatus status, String title) {
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
