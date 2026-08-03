package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class InvalidSettingCodeException extends ApiException {
    private final String detail;
    private final List<FieldError> fieldErrors;

    public InvalidSettingCodeException(String detail) {
        this(detail, List.of(), null);
    }

    public InvalidSettingCodeException(String detail, Throwable cause) {
        this(detail, List.of(), cause);
    }

    public InvalidSettingCodeException(String detail, List<FieldError> fieldErrors) {
        this(detail, fieldErrors, null);
    }

    private InvalidSettingCodeException(String detail, List<FieldError> fieldErrors, Throwable cause) {
        super(detail);
        this.detail = detail == null ? "지원하지 않거나 누락된 설정 코드가 있습니다" : detail;
        this.fieldErrors = List.copyOf(fieldErrors);
        if (cause != null) {
            initCause(cause);
        }
    }

    @Override
    public String code() {
        return "INVALID_SETTING_CODE";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String detail() {
        return detail;
    }

    @Override
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
