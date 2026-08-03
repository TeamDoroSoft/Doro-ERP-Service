package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import com.dorosoft.erp.store.domain.schedule.OperatingScheduleViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class ServiceWindowOutsideBusinessHoursException extends ApiException {
    private final String detail;
    private final List<FieldError> fieldErrors;

    public ServiceWindowOutsideBusinessHoursException(OperatingScheduleViolationException cause) {
        super(cause.getMessage());
        this.detail = cause.getMessage();
        this.fieldErrors = List.of(new FieldError(cause.field(), cause.reason().name()));
        initCause(cause);
    }

    @Override
    public String code() {
        return "SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS";
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
