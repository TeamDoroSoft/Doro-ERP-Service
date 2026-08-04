-- 선택한 옵션의 요청 순서를 영수증 표시 순서로 보존한다.
-- 기존 Row는 안정적인 PK 순서로 한 번 백필한다.
ALTER TABLE order_item_option
    ADD COLUMN option_order INT NULL AFTER option_id;

UPDATE order_item_option item_option
    JOIN
    (
        SELECT order_item_option_id,
               ROW_NUMBER() OVER (PARTITION BY order_item_id ORDER BY order_item_option_id) - 1 AS calculated_option_order
        FROM order_item_option
    ) ranked ON ranked.order_item_option_id = item_option.order_item_option_id
SET item_option.option_order = ranked.calculated_option_order;

ALTER TABLE order_item_option
    MODIFY COLUMN option_order INT NOT NULL,
    ADD CONSTRAINT uk_order_item_option_item_order UNIQUE (order_item_id, option_order),
    ADD CONSTRAINT ck_order_item_option_order CHECK (option_order >= 0);
