package com.dorosoft.erp.platform.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Stable web error contract implemented by common and business-module error codes.
 */
public interface ProblemCode {

    String code();

    String title();

    HttpStatus status();

    default URI type() {
        return URI.create("urn:doro-erp:problem:" + code().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    default ProblemDetail toProblemDetail(
            String requestId,
            List<ProblemFieldError> fieldErrors,
            String detailText) {
        ProblemDetail detail = ProblemDetail.forStatus(status());
        detail.setType(type());
        detail.setTitle(title());
        detail.setProperty("code", code());
        detail.setProperty("requestId", requestId);
        detail.setProperty("fieldErrors", fieldErrors == null ? List.of() : List.copyOf(fieldErrors));
        if (detailText != null && !detailText.isBlank()) {
            detail.setDetail(detailText);
        }
        detail.setInstance(URI.create("urn:doro-erp:request:" + requestId));
        return detail;
    }
}
