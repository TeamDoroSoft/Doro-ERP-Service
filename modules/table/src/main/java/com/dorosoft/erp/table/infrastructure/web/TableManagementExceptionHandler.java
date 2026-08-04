package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableManagementException;
import com.dorosoft.erp.table.application.TableSessionCloseBlockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dorosoft.erp.table")
class TableManagementExceptionHandler {

    @ExceptionHandler(TableSessionCloseBlockedException.class)
    ResponseEntity<ProblemDetail> handleCloseBlocked(
            TableSessionCloseBlockedException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code().name());
        problem.setProperty(
                "blockers",
                exception.blockers().stream()
                        .map(blocker -> Map.of("code", blocker.code(), "message", blocker.message()))
                        .toList());
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(TableManagementException.class)
    ResponseEntity<ProblemDetail> handle(
            TableManagementException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code().name());
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        org.springframework.http.HttpStatus.PRECONDITION_FAILED,
                        "Table version does not match If-Match.");
        problem.setProperty("code", "PRECONDITION_FAILED");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        return ResponseEntity.status(org.springframework.http.HttpStatus.PRECONDITION_FAILED).body(problem);
    }
}
