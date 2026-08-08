package com.dorosoft.erp.commerce.domain.catalog;

/**
 * Domain 규칙 위반 사유. Framework와 HTTP 계약에 의존하지 않는 순수 Code다.
 */
public enum CatalogViolation {
    TENANT_REQUIRED("tenantId"),
    CATEGORY_REQUIRED("categoryId"),
    IDENTIFIER_REQUIRED("id"),
    STATUS_REQUIRED("active"),
    NAME_REQUIRED("name"),
    NAME_TOO_LONG("name"),
    DESCRIPTION_TOO_LONG("description"),
    PRICE_NEGATIVE("price"),
    PRICE_TOO_LARGE("price"),
    DISPLAY_ORDER_NEGATIVE("displayOrder"),
    DISPLAY_ORDER_TOO_LARGE("displayOrder");

    private final String field;

    CatalogViolation(String field) {
        this.field = field;
    }

    public String field() {
        return field;
    }
}
