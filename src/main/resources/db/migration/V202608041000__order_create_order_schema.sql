-- Order 모듈의 금액 계산 Slice(확정된 add()·multiply() 정책)에 필요한 최소 스키마를 생성한다.
-- 채널·이행 유형·상태·Table Session·멱등성 등 나머지 06 공통 주문 관리 컬럼은 이 Migration의 범위가 아니며
-- 해당 기능 구현 시 Forward-fix Migration으로 추가한다.

-- 주문 Aggregate Root. 총액은 Item lineAmount의 checked add 합계다.
CREATE TABLE orders
(
    order_id     BINARY(16)  NOT NULL,
    total_amount BIGINT      NOT NULL,
    currency     CHAR(3)     NOT NULL DEFAULT 'KRW',
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (order_id),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 주문 시점 상품명·단가·수량·금액 Snapshot(Catalog 변경과 분리).
CREATE TABLE order_item
(
    order_item_id    BINARY(16)   NOT NULL,
    order_id         BINARY(16)   NOT NULL,
    product_id       BINARY(16)   NOT NULL,
    product_name     VARCHAR(100) NOT NULL,
    base_unit_price  BIGINT       NOT NULL,
    unit_price       BIGINT       NOT NULL,
    quantity         INT          NOT NULL,
    line_amount      BIGINT       NOT NULL,
    stock_managed    BOOLEAN      NOT NULL,
    catalog_revision BIGINT       NOT NULL,
    PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT ck_order_item_base_unit_price CHECK (base_unit_price >= 0),
    CONSTRAINT ck_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_line_amount CHECK (line_amount >= 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 주문 시점 선택 옵션명·추가 금액 Snapshot.
CREATE TABLE order_item_option
(
    order_item_option_id BINARY(16)   NOT NULL,
    order_item_id        BINARY(16)   NOT NULL,
    option_id             BINARY(16)  NOT NULL,
    option_name            VARCHAR(100) NOT NULL,
    additional_price        BIGINT      NOT NULL,
    PRIMARY KEY (order_item_option_id),
    CONSTRAINT fk_order_item_option_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id),
    CONSTRAINT ck_order_item_option_additional_price CHECK (additional_price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
