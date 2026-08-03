-- API 명세 공통 계약: "생성 API는 Idempotency-Key를 요구한다"는 Product(MENU-04)에 국한된 규칙이
-- 아니라 모든 생성 API에 적용된다. category와 product_media 생성에도 같은 패턴(Idempotency-Key +
-- 요청 Hash 비교 컬럼)을 소급 적용한다. 이미 적용된 MENU-01·MENU-04 Migration은 수정하지 않는다.
ALTER TABLE category
    ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER name,
    ADD COLUMN idempotency_request_hash VARCHAR(64) NULL AFTER idempotency_key,
    ADD CONSTRAINT uk_category_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE product_media
    ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER checksum_sha256,
    ADD COLUMN idempotency_request_hash VARCHAR(64) NULL AFTER idempotency_key,
    ADD CONSTRAINT uk_product_media_idempotency_key UNIQUE (idempotency_key);
