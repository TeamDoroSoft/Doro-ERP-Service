package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for employee login, Session validation and reauthentication (ADR-02-002/003/006/007/011). */
public enum AuthProblemCode implements ProblemCode {

    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.", null),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", 60),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", null),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다.", null),
    SESSION_ABSOLUTE_EXPIRED(HttpStatus.UNAUTHORIZED, "Session이 만료되어 다시 로그인해야 합니다.", null),
    SESSION_INVALIDATED(HttpStatus.UNAUTHORIZED, "Session이 무효화되었습니다. 다시 로그인해 주세요.", null),
    REAUTHENTICATION_REQUIRED(HttpStatus.FORBIDDEN, "중요 작업을 진행하려면 재인증이 필요합니다.", null),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 먼저 변경해야 합니다.", null),
    SESSION_VALIDATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 인증을 확인할 수 없습니다.", null);

    private final HttpStatus status;
    private final String title;
    private final Integer retryAfterSeconds;

    AuthProblemCode(HttpStatus status, String title, Integer retryAfterSeconds) {
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
