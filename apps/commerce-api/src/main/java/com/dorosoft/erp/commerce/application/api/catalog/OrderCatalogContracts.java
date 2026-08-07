package com.dorosoft.erp.commerce.application.api.catalog;

import java.util.List;
import java.util.UUID;

/**
 * 주문용 Catalog 조회 계약 (FR-CATALOG-005, 03 기술 설계 §3).
 *
 * <p>주문 금액의 신뢰 원천은 Server-side Catalog다. 요청은 상품 식별자와 수량만 전달하며
 * 단가·상품명·합계를 Client가 보낼 수 없다. 계약에 가격 입력 자체가 없으므로 가격 변조는
 * 검증으로 걸러내는 것이 아니라 구조적으로 불가능하다.
 */
public final class OrderCatalogContracts {

    private OrderCatalogContracts() {
    }

    /** 주문 한 줄의 요청. 가격 Field를 의도적으로 두지 않는다. */
    public record OrderLineRequest(UUID productId, int quantity) {
    }

    /**
     * 주문 시점에 고정되는 상품 Snapshot.
     *
     * <p>이후 Catalog에서 상품명이나 가격이 바뀌어도 이미 만들어진 Snapshot은 변하지 않는다.
     * Order Aggregate는 이 값을 그대로 저장해야 하며 조회·합계 계산에서 Catalog의 현재 가격을
     * 다시 읽으면 안 된다 (FR-CATALOG-005, FR-ORDER-003).
     */
    public record ProductSnapshot(UUID productId, String productName, long unitPrice) {
    }

    /** 서버가 계산한 주문 한 줄. {@code lineAmount = unitPrice * quantity}다. */
    public record OrderLineQuote(ProductSnapshot product, int quantity, long lineAmount) {
    }

    /** 주문 전체의 서버 계산 결과. */
    public record OrderQuote(String currency, List<OrderLineQuote> lines, long totalAmount) {

        public OrderQuote {
            lines = List.copyOf(lines);
        }
    }
}
