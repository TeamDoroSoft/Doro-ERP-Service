package com.dorosoft.erp.catalog.presentation.error;

import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.category.InvalidDisplayOrderException;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.media.InvalidMediaObjectException;
import com.dorosoft.erp.catalog.domain.media.MediaAlreadyRejectedException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaPublishConflictException;
import com.dorosoft.erp.catalog.domain.media.MediaUploadChangedException;
import com.dorosoft.erp.catalog.domain.media.UnsupportedMediaContentTypeException;
import com.dorosoft.erp.catalog.domain.product.InvalidPriceException;
import com.dorosoft.erp.catalog.domain.product.InvalidProductOptionsException;
import com.dorosoft.erp.catalog.domain.product.MediaNotReadyException;
import com.dorosoft.erp.catalog.domain.product.OptionOmissionNotAllowedException;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import com.dorosoft.erp.catalog.domain.query.InvalidCursorException;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Catalog 도메인 예외를 API 명세의 오류 코드로 번역한다. */
@RestControllerAdvice(basePackages = "com.dorosoft.erp.catalog.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogProblemAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.FORBIDDEN, request);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ProblemDetail> categoryNotFound(CategoryNotFoundException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.CATEGORY_NOT_FOUND, request);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> productNotFound(ProductNotFoundException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.PRODUCT_NOT_FOUND, request);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ProblemDetail> mediaNotFound(MediaNotFoundException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.MEDIA_NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidDisplayOrderException.class)
    public ResponseEntity<ProblemDetail> invalidDisplayOrder(InvalidDisplayOrderException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.INVALID_DISPLAY_ORDER, request);
    }

    @ExceptionHandler(InvalidPriceException.class)
    public ResponseEntity<ProblemDetail> invalidPrice(InvalidPriceException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.INVALID_PRICE, request);
    }

    @ExceptionHandler(InvalidProductOptionsException.class)
    public ResponseEntity<ProblemDetail> invalidProductOptions(InvalidProductOptionsException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.INVALID_PRODUCT_OPTIONS, request);
    }

    @ExceptionHandler(OptionOmissionNotAllowedException.class)
    public ResponseEntity<ProblemDetail> optionOmissionNotAllowed(
            OptionOmissionNotAllowedException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.OPTION_OMISSION_NOT_ALLOWED, request);
    }

    @ExceptionHandler({InvalidMediaObjectException.class, UnsupportedMediaContentTypeException.class})
    public ResponseEntity<ProblemDetail> invalidMediaObject(RuntimeException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.INVALID_MEDIA_OBJECT, request);
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ProblemDetail> invalidCursor(InvalidCursorException exception, HttpServletRequest request) {
        return problem(ApiErrorCode.VALIDATION_FAILED, request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ProblemDetail> idempotencyKeyReused(IdempotencyKeyReusedException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.IDEMPOTENCY_KEY_REUSED, request);
    }

    @ExceptionHandler(MediaUploadChangedException.class)
    public ResponseEntity<ProblemDetail> mediaUploadChanged(MediaUploadChangedException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.MEDIA_UPLOAD_CHANGED, request);
    }

    @ExceptionHandler(MediaPublishConflictException.class)
    public ResponseEntity<ProblemDetail> mediaPublishConflict(MediaPublishConflictException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.MEDIA_PUBLISH_CONFLICT, request);
    }

    @ExceptionHandler(MediaAlreadyRejectedException.class)
    public ResponseEntity<ProblemDetail> mediaAlreadyRejected(MediaAlreadyRejectedException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.MEDIA_UPLOAD_REJECTED, request);
    }

    @ExceptionHandler(MediaNotReadyException.class)
    public ResponseEntity<ProblemDetail> mediaNotReady(MediaNotReadyException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.MEDIA_NOT_READY, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> versionConflict(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(CatalogErrorCode.VERSION_CONFLICT, request);
    }

    private ResponseEntity<ProblemDetail> problem(ProblemCode code, HttpServletRequest request) {
        String requestId = CatalogRequestId.from(request);
        ProblemDetail detail = code.toProblemDetail(requestId, List.of(), title(code));
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }

    private static String title(ProblemCode code) {
        return code instanceof CatalogErrorCode catalogCode ? catalogCode.defaultDetail() : code.title();
    }
}
