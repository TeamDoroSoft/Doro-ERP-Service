package com.dorosoft.erp.commerce.domain.catalog;

/**
 * Catalog Domain 불변 규칙 위반. Application 계층이 공개 오류 계약으로 변환한다.
 */
public class CatalogRuleException extends RuntimeException {

    private final CatalogViolation violation;

    public CatalogRuleException(CatalogViolation violation) {
        super("catalog rule violated: " + violation.name());
        this.violation = violation;
    }

    public CatalogViolation violation() {
        return violation;
    }

    public String field() {
        return violation.field();
    }
}
