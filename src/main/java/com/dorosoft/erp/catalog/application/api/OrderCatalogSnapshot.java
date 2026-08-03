package com.dorosoft.erp.catalog.application.api;

import java.util.List;
import java.util.UUID;

/**
 * 주문 생성 시점의 서버 가격 Snapshot 원본(판매 가능성 판정 명세). Order는 이 값을 자신의
 * 주문 항목에 복사해 이후 Catalog 변경과 분리한다. 수량 적용과 합계 계산은 Order가 담당한다.
 */
public record OrderCatalogSnapshot(
        UUID productId,
        String productName,
        long baseUnitPrice,
        List<SelectedOptionSnapshot> selectedOptions,
        boolean stockManaged,
        long catalogRevision) {}
