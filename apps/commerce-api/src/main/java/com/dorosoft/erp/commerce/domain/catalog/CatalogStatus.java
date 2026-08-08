package com.dorosoft.erp.commerce.domain.catalog;

/**
 * Category와 Product의 판매 노출 상태. 삭제 대신 {@link #INACTIVE}로 전환해 기존 주문 참조를 보존한다.
 */
public enum CatalogStatus {
    ACTIVE,
    INACTIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
