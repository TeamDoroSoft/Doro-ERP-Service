package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for Idempotency-Key conflicts (ADR-02-014). */
public enum IdempotencyProblemCode implements ProblemCode {

    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "동일한 요청이 이미 처리 중입니다.", 1),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "이미 사용된 Idempotency-Key이며 요청 내용이 다릅니다.", null);

    private final HttpStatus status;
    private final String title;
    private final Integer retryAfterSeconds;

    IdempotencyProblemCode(HttpStatus status, String title, Integer retryAfterSeconds) {
        this.status = status;
        this.title = title;
        this.retryAfterSeconds = retryAfterSeconds;
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

    @Override
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
