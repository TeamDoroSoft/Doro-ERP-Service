package com.dorosoft.erp.identity.application.error;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Stable Feature 01 API error catalogue. */
public enum IdentityErrorCode implements ProblemCode {
    VALIDATION_FAILED("요청 값 오류", HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),
    CURRENT_PASSWORD_MISMATCH("현재 비밀번호 불일치", HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    INVALID_ROLE("유효하지 않은 역할", HttpStatus.BAD_REQUEST, "사용할 수 없는 역할입니다."),
    INVALID_PERMISSION("유효하지 않은 권한", HttpStatus.BAD_REQUEST, "지원하지 않는 권한입니다."),
    INVALID_PERMISSION_COMBINATION("유효하지 않은 권한 조합", HttpStatus.BAD_REQUEST, "허용되지 않는 권한 조합입니다."),
    IDEMPOTENCY_KEY_REQUIRED("멱등 키 필요", HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    INVALID_CURSOR("유효하지 않은 커서", HttpStatus.BAD_REQUEST, "커서가 유효하지 않습니다."),
    INVALID_CREDENTIALS("로그인 실패", HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED("인증 필요", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN("접근 거부", HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),
    CSRF_TOKEN_INVALID("CSRF 토큰 오류", HttpStatus.FORBIDDEN, "CSRF 토큰이 유효하지 않습니다."),
    PASSWORD_CHANGE_REQUIRED("비밀번호 변경 필요", HttpStatus.FORBIDDEN, "먼저 비밀번호를 변경해야 합니다."),
    ACCOUNT_NOT_FOUND("계정 없음", HttpStatus.NOT_FOUND, "대상 계정을 찾을 수 없습니다."),
    LOGIN_ID_DUPLICATE("로그인 ID 중복", HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),
    IDEMPOTENCY_KEY_REUSED("멱등 키 재사용", HttpStatus.CONFLICT, "다른 요청에 사용한 멱등 키입니다."),
    REPRESENTATIVE_ROLE_IMMUTABLE("대표 역할 변경 불가", HttpStatus.CONFLICT, "대표 계정의 관리자 역할은 변경할 수 없습니다."),
    PRECONDITION_FAILED("수정 충돌", HttpStatus.PRECONDITION_FAILED, "다른 사용자가 먼저 변경했습니다."),
    PRECONDITION_REQUIRED("버전 조건 필요", HttpStatus.PRECONDITION_REQUIRED, "If-Match 헤더가 필요합니다."),
    LOGIN_RATE_LIMITED("로그인 요청 제한", HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주십시오."),
    INTERNAL_SERVER_ERROR("서버 오류", HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다."),
    AUTHENTICATION_UNAVAILABLE("인증 서비스 일시 중단", HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스를 사용할 수 없습니다."),
    IDEMPOTENCY_UNAVAILABLE("멱등 처리 일시 중단", HttpStatus.SERVICE_UNAVAILABLE, "같은 요청으로 잠시 후 다시 시도하십시오."),
    PRIVACY_ACCESS_LOG_UNAVAILABLE("개인정보 접근기록 일시 중단", HttpStatus.SERVICE_UNAVAILABLE, "개인정보 접근기록을 저장할 수 없습니다.");

    private final String title;
    private final HttpStatus status;
    private final String defaultDetail;

    IdentityErrorCode(String title, HttpStatus status, String defaultDetail) {
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
