package com.dorosoft.erp.order.domain.money;

/**
 * OrderAmount에 음수가 들어옴. Catalog·Item 계산 결과는 항상 0 이상이어야 하는 내부 불변식 위반이며,
 * 정상 입력 경로에서는 도달하지 않는다. 외부에 노출되는 API 오류 코드는 없다.
 */
public class NegativeOrderAmountException extends RuntimeException {

    public NegativeOrderAmountException(long amount) {
        super("금액은 0 이상의 정수 원화여야 합니다. 입력값=" + amount);
    }
}
