package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class InvalidSettingCodeException extends ApiException {
    private final String detail;
    private final List<FieldError> fieldErrors;

    public InvalidSettingCodeException(String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.detail = detail;
        this.fieldErrors = List.copyOf(fieldErrors);
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
