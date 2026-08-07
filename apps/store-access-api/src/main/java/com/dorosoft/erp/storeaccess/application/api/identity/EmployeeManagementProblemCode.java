package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for employee create/Role/status management (ADR-02-010). */
public enum EmployeeManagementProblemCode implements ProblemCode {

    EMPLOYEE_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "직원 관리 권한이 없습니다."),
    SELF_ACCOUNT_ADMIN_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "자기 계정은 관리자 API로 변경할 수 없습니다."),
    LAST_ACTIVE_OWNER_REQUIRED(HttpStatus.CONFLICT, "마지막 활성 OWNER는 변경할 수 없습니다."),
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String title;

    EmployeeManagementProblemCode(HttpStatus status, String title) {
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
