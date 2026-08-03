package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.store.domain.schedule.OperatingScheduleViolationException;
import org.springframework.http.HttpStatus;

public final class ServiceWindowOutsideBusinessHoursException extends ApiException {
    private final String detail;

    public ServiceWindowOutsideBusinessHoursException(OperatingScheduleViolationException cause) {
        super(cause.getMessage());
        this.detail = cause.getMessage();
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
}
