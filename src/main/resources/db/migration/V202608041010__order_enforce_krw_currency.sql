-- Order 금액은 다중 통화를 지원하지 않고 정수 원화(KRW)로만 저장한다.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_currency_krw CHECK (currency = 'KRW');
