package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for password change/reset (ADR-02-009). */
public enum PasswordManagementProblemCode implements ProblemCode {

    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 정책을 만족하지 않습니다."),
    PASSWORD_REUSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다."),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String title;

    PasswordManagementProblemCode(HttpStatus status, String title) {
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
