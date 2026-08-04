package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableManagementException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TableQrPublicAccessController.class)
class TableQrPublicAccessExceptionHandler {

    @ExceptionHandler(TableManagementException.class)
    ResponseEntity<ProblemDetail> handle(
            TableManagementException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code().name());
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore())
                .body(problem);
    }
}
