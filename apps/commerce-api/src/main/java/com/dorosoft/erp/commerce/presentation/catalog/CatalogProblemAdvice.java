package com.dorosoft.erp.commerce.presentation.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Database Unique Constraint 최종 경쟁을 공개 Conflict 계약으로 변환한다.
 *
 * <p>Application의 선조회만 믿지 않고 Constraint를 최종 방어선으로 사용하기 때문에 필요하다.
 */
@RestControllerAdvice(assignableTypes = {CatalogCategoryController.class, CatalogProductController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogProblemAdvice {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        String requestId = attribute instanceof String text && !text.isBlank()
                ? text
                : "req-" + UUID.randomUUID();
        ProblemDetail problemDetail = CatalogErrorCode.CATALOG_VERSION_CONFLICT.toProblemDetail(
                requestId, List.of(), "다른 변경과 충돌했습니다. 최신 정보를 다시 불러온 뒤 저장하세요.");
        return ResponseEntity.status(CatalogErrorCode.CATALOG_VERSION_CONFLICT.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
