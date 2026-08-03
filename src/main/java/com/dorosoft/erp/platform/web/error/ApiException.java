package com.dorosoft.erp.platform.web.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    protected ApiException() {
    }

    protected ApiException(String message) {
        super(message);
    }

    public abstract String code();

    public abstract HttpStatus status();

    public abstract String detail();

    public List<FieldError> fieldErrors() {
        return List.of();
    }
}
