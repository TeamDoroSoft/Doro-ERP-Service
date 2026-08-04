package com.dorosoft.erp.catalog.domain.product;

import java.util.Objects;

/**
 * 금액과 통화 쌍을 감싸는 값 객체(도메인 클래스 다이어그램). 음수 검증은 하지 않는다 - Product·
 * ProductOption이 자신의 맥락에 맞는 예외 메시지(상품 가격 vs 옵션 추가 금액)로 직접 검증한다.
 * 다중 통화는 기능 요구사항의 제외 범위라 currency는 항상 KRW다.
 */
public record Money(long amount, String currency) {

    private static final String KRW = "KRW";

    public Money {
        Objects.requireNonNull(currency, "currency는 필수다");
    }

    public static Money of(long amount) {
        return new Money(amount, KRW);
    }
}
