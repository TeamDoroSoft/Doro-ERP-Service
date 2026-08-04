package com.dorosoft.erp.platform.web;

import java.util.Collections;
import java.util.List;
import org.springframework.http.ProblemDetail;

public class ProblemAwareException extends RuntimeException {

    private final ProblemCode code;
    private final String detail;
    private final List<ProblemFieldError> fieldErrors;

    public ProblemAwareException(ProblemCode code, String detail) {
        this(code, detail, Collections.emptyList(), null);
    }

    public ProblemAwareException(ProblemCode code, String detail, List<ProblemFieldError> fieldErrors) {
        this(code, detail, fieldErrors, null);
    }

    public ProblemAwareException(ProblemCode code, String detail, List<ProblemFieldError> fieldErrors, Throwable cause) {
        super(detail, cause);
        this.code = code;
        this.detail = detail;
        this.fieldErrors = fieldErrors == null ? Collections.emptyList() : List.copyOf(fieldErrors);
    }

    public ProblemCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public List<ProblemFieldError> fieldErrors() {
        return fieldErrors;
    }

    public ProblemDetail toProblemDetail(String requestId) {
        return code.toProblemDetail(requestId, fieldErrors, detail);
    }
}
