package com.dorosoft.erp.platform.web.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailFactory {

    private static final String PROBLEM_URN_PREFIX = "urn:doro-erp:problem:";
    private static final String REQUEST_URN_PREFIX = "urn:doro-erp:request:";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(
            HttpStatus status, String code, String detail, String requestId, List<FieldError> fieldErrors) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(PROBLEM_URN_PREFIX + toKebabCase(code)));
        problemDetail.setInstance(URI.create(REQUEST_URN_PREFIX + requestId));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("requestId", requestId);
        problemDetail.setProperty("fieldErrors", fieldErrors == null ? List.of() : fieldErrors);
        return problemDetail;
    }

    public static String toKebabCase(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
