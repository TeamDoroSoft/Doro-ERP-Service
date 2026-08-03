package com.dorosoft.erp.store.presentation.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import com.dorosoft.erp.platform.web.error.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class IfMatchMissingOrInvalidException extends ApiException {

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
        return "If-Match 헤더가 필요하며 유효한 버전이어야 합니다";
    }

    @Override
    public List<FieldError> fieldErrors() {
        return List.of(new FieldError("If-Match", "REQUIRED"));
    }
}
