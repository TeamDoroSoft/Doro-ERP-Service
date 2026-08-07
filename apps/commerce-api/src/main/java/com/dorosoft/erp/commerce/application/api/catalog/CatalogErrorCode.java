package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/**
 * Catalog 공개 오류 계약. 내부 예외·SQL·Secret을 노출하지 않는 안정 Code만 제공한다.
 */
public enum CatalogErrorCode implements ProblemCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증 실패"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증 필요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한 부족"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category를 찾을 수 없음"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없음"),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "Category 이름 중복"),
    PRODUCT_NAME_DUPLICATED(HttpStatus.CONFLICT, "상품 이름 중복"),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "현재 version 필요"),
    CATALOG_VERSION_CONFLICT(HttpStatus.CONFLICT, "동시 수정 충돌");

    private final HttpStatus status;
    private final String title;

    CatalogErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
