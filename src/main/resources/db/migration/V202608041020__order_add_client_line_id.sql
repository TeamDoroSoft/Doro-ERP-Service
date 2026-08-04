-- 요청 Item 식별자를 주문 Snapshot에 보존하고 같은 주문 안의 중복을 방지한다.
ALTER TABLE order_item
    ADD COLUMN client_line_id VARCHAR(50) NOT NULL AFTER order_id,
    ADD CONSTRAINT uk_order_item_order_client_line UNIQUE (order_id, client_line_id);

ALTER TABLE order_item_option
    ADD CONSTRAINT uk_order_item_option_item_option UNIQUE (order_item_id, option_id);
