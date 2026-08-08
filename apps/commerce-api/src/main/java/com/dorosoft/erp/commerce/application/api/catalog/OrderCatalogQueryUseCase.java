package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderLineRequest;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderQuote;
import java.util.List;

/**
 * 주문 생성이 사용하는 Commerce 내부 Catalog Query.
 *
 * <p>같은 서비스 안의 Application API이므로 네트워크 호출이나 Catalog 복제본을 만들지 않는다
 * (03 기술 설계 §1). Order Application은 주문을 저장하기 직전에 이 Query를 호출해
 * 판매 가능 여부를 재확인하고 서버 가격으로 금액을 계산해야 한다.
 */
public interface OrderCatalogQueryUseCase {

    /**
     * 주문 줄 목록을 검증하고 서버 가격으로 금액을 계산한다.
     *
     * <p>메뉴 조회 시점과 주문 시점 사이에 직원이 품절 처리하거나 관리자가 비활성화할 수 있으므로
     * 이 Query가 두 번째 검증 지점이다. 다음 중 하나라도 걸리면 주문 전체를 거절한다.
     *
     * <ul>
     *   <li>다른 Tenant 상품이거나 존재하지 않음 → {@code PRODUCT_NOT_FOUND}</li>
     *   <li>상품 비활성·Category 비활성·품절 → {@code PRODUCT_NOT_SELLABLE}</li>
     *   <li>수량이 1 미만이거나 줄 목록이 비어 있음 → {@code VALIDATION_FAILED}</li>
     * </ul>
     *
     * <p>다른 Tenant 상품은 판매 불가가 아니라 "없음"으로 응답해 존재 여부를 노출하지 않는다.
     */
    OrderQuote quoteOrderLines(List<OrderLineRequest> lines);
}
