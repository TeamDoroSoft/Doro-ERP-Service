package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.store.domain.schedule.OperatingScheduleViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class OverlappingServiceWindowException extends ApiException {
    private final String detail;
    private final List<FieldError> fieldErrors;

    public OverlappingServiceWindowException(OperatingScheduleViolationException cause) {
        super(cause.getMessage());
        this.detail = cause.getMessage();
        this.fieldErrors = List.of(new FieldError(cause.field(), cause.reason().name()));
        initCause(cause);
    }

    @Override
    public String code() {
        return "OVERLAPPING_SERVICE_WINDOW";
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
