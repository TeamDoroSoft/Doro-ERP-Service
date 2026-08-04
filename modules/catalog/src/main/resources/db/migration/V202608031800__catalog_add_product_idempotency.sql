-- FR-MENU-001의 생성 멱등성: 같은 Idempotency-Key와 같은 요청 내용의 재요청은 기존 결과를 반환하고,
-- 다른 내용이면 거부해야 한다(MENU-04). MENU-01이 만든 product 테이블은 수정하지 않고
-- Forward-fix Migration으로 두 컬럼을 추가한다.
ALTER TABLE product
    ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER stock_managed,
    ADD COLUMN idempotency_request_hash VARCHAR(64) NULL AFTER idempotency_key,
    ADD CONSTRAINT uk_product_idempotency_key UNIQUE (idempotency_key);
