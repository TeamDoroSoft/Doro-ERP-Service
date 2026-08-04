-- 요청 Item 표시 순서와 옵션 금액 합계를 주문 시점 Snapshot으로 보존한다.
-- 기존 Row는 안정적인 PK 순서와 저장된 Option Snapshot 합계로 한 번 백필한다.
ALTER TABLE order_item
    ADD COLUMN line_order INT NULL AFTER client_line_id,
    ADD COLUMN option_unit_amount BIGINT NULL AFTER base_unit_price;

UPDATE order_item item
    JOIN
    (
        SELECT order_item_id,
               ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY order_item_id) - 1 AS calculated_line_order
        FROM order_item
    ) ranked ON ranked.order_item_id = item.order_item_id
SET item.line_order = ranked.calculated_line_order;

UPDATE order_item item
    LEFT JOIN
    (
        SELECT order_item_id, COALESCE(SUM(additional_price), 0) AS calculated_option_amount
        FROM order_item_option
        GROUP BY order_item_id
    ) option_totals ON option_totals.order_item_id = item.order_item_id
SET item.option_unit_amount = COALESCE(option_totals.calculated_option_amount, 0);

ALTER TABLE order_item
    MODIFY COLUMN line_order INT NOT NULL,
    MODIFY COLUMN option_unit_amount BIGINT NOT NULL,
    ADD CONSTRAINT uk_order_item_order_line_order UNIQUE (order_id, line_order),
    ADD CONSTRAINT ck_order_item_line_order CHECK (line_order BETWEEN 0 AND 99),
    ADD CONSTRAINT ck_order_item_option_unit_amount CHECK (option_unit_amount >= 0);
