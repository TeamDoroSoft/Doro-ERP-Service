package com.dorosoft.erp.order.application.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가격 변조 방지(MENU-10 인수 검증). 클라이언트가 보내는 주문 요청은 상품·옵션 ID와 수량만 전달하고,
 * 금액은 항상 {@link com.dorosoft.erp.catalog.application.api.OrderCatalogReader}를 통해 서버가
 * Catalog에서 다시 계산한다({@link CalculateOrderService}). 이 계약이 앞으로도 유지되도록 클라이언트
 * 입력 타입에 금액 관련 필드가 절대 추가되지 않는지 고정한다 - 만약 이 테스트가 깨진다면 클라이언트가
 * 가격을 직접 지정할 수 있는 경로가 생겼다는 뜻이므로 반드시 리뷰가 필요하다.
 */
@DisplayName("주문 금액 신뢰 경계(가격 변조 방지) 구조 제약 - 클라이언트 입력 타입에 금액 필드가 없는지 확인한다")
class OrderPriceTrustBoundaryContractTest {

    private static final List<String> PRICE_RELATED_KEYWORDS = List.of("price", "amount", "cost", "fee");

    @Test
    @DisplayName("OrderItemSelection(클라이언트 주문 항목)은 productId·optionIds·quantity·clientLineId 외 금액 필드를 갖지 않는다")
    void orderItemSelectionHasNoPriceField() {
        assertNoPriceRelatedComponent(OrderItemSelection.class);
    }

    @Test
    @DisplayName("CalculateOrderCommand(클라이언트 주문 계산 요청)는 금액 필드를 갖지 않는다")
    void calculateOrderCommandHasNoPriceField() {
        assertNoPriceRelatedComponent(CalculateOrderCommand.class);
    }

    private static void assertNoPriceRelatedComponent(Class<?> recordType) {
        assertThat(recordType.isRecord()).as(recordType.getSimpleName() + "은 record여야 한다").isTrue();

        for (RecordComponent component : recordType.getRecordComponents()) {
            assertThat(containsPriceKeyword(component.getName()))
                    .as(
                            recordType.getSimpleName()
                                    + "."
                                    + component.getName()
                                    + " - 클라이언트 입력 타입은 금액을 직접 전달받으면 안 된다(서버가 Catalog에서 다시 계산한다)")
                    .isFalse();
        }
        for (Field field : recordType.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertThat(containsPriceKeyword(field.getName()))
                    .as(recordType.getSimpleName() + "." + field.getName() + " - 금액 관련 필드가 있으면 안 된다")
                    .isFalse();
        }
    }

    private static boolean containsPriceKeyword(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return PRICE_RELATED_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
