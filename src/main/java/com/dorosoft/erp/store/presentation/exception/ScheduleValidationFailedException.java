package com.dorosoft.erp.store.presentation.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class ScheduleValidationFailedException extends ApiException {

    private final List<FieldError> fieldErrors;

    public ScheduleValidationFailedException(List<FieldError> fieldErrors) {
        super("운영 일정 요청 값이 올바르지 않습니다");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    @Override
    public String code() {
        return "VALIDATION_FAILED";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String detail() {
        return "운영 일정 요청 값이 올바르지 않습니다";
    }

    @Override
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
