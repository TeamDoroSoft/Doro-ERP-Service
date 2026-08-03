package com.dorosoft.erp.catalog.application.api;

import java.util.UUID;

/**
 * Inventory가 재고 작업 대상인지 확인할 때 쓰는 읽기 전용 공개 계약(기능 연계 명세).
 * 다른 모듈은 이 Interface로만 Catalog를 참조하며 Repository·Entity를 직접 쓰지 않는다.
 */
public interface StockManagementPolicyReader {

    /** 현재 업체에 없는 productId를 전달하면 ProductNotFoundException을 던진다. */
    boolean isStockManaged(UUID productId);
}
