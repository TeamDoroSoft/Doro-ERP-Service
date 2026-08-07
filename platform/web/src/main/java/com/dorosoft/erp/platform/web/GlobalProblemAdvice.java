package com.dorosoft.erp.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.validation.ObjectError;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalProblemAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            org.springframework.http.HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String requestId = resolveRequestId(extractServletRequest(request));
        List<ProblemFieldError> fieldErrors = new ArrayList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : "_global";
            String code = error.getCode();
            fieldErrors.add(new ProblemFieldError(field, code != null ? code : "VALIDATION"));
        }

        ProblemDetail problemDetail = ApiErrorCode.VALIDATION_FAILED
                .toProblemDetail(requestId, fieldErrors, "요청 값이 유효하지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String requestId = resolveRequestId(extractServletRequest(request));
        ApiErrorCode code = status.is4xxClientError()
                ? ApiErrorCode.VALIDATION_FAILED
                : ApiErrorCode.INTERNAL_SERVER_ERROR;
        String safeDetail = status.is4xxClientError()
                ? "요청 형식이 유효하지 않습니다."
                : "요청을 처리하지 못했습니다.";
        ProblemDetail problemDetail = code.toProblemDetail(requestId, List.of(), safeDetail);
        problemDetail.setStatus(status.value());
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(ProblemAwareException.class)
    public ResponseEntity<ProblemDetail> handleProblemAwareException(
            ProblemAwareException ex,
            HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problemDetail = ex.toProblemDetail(requestId);
        if (problemDetail.getInstance() == null) {
            problemDetail.setInstance(java.net.URI.create("urn:doro-erp:request:" + requestId));
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        Integer retryAfterSeconds = ex.code().retryAfterSeconds();
        if (retryAfterSeconds != null) {
            response = response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        return response
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problemDetail = ApiErrorCode.INTERNAL_SERVER_ERROR
                .toProblemDetail(requestId, List.of(), "요청을 처리하지 못했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private HttpServletRequest extractServletRequest(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest();
        }
        return null;
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return "req-" + UUID.randomUUID();
        }
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        if (requestId instanceof String requestIdText && !requestIdText.isBlank()) {
            return requestIdText;
        }
        return "req-" + UUID.randomUUID();
    }
}
