-- 상품·메뉴 관리(Catalog) 모듈의 최초 스키마를 생성하는 Migration이다.
-- 정렬 동시성 기준(catalog_revision), 카테고리, 상품, 상품 옵션과 상품 이미지 Metadata를 다룬다.
-- 초기 데이터(INSERT)는 넣지 않으며, catalog_revision 1행 초기화는 애플리케이션 Bootstrap이 담당한다.

-- 메뉴 전체 변경 번호. 카테고리·상품 전체 정렬의 동시성 기준(업체 Schema당 단일 행).
CREATE TABLE catalog_revision
(
    catalog_id BINARY(16)  NOT NULL,
    revision   BIGINT      NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (catalog_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 상품 이미지 Metadata. Binary는 S3에 두고 여기는 Staging/Public Key, 검증 상태만 저장한다(ADR-007).
CREATE TABLE product_media
(
    media_id              BINARY(16)   NOT NULL,
    catalog_id            BINARY(16)   NOT NULL,
    staging_object_key    VARCHAR(512) NOT NULL,
    published_object_key  VARCHAR(512) NULL,
    object_etag           VARCHAR(128) NULL,
    checksum_sha256       VARCHAR(44)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    content_type          VARCHAR(50)  NOT NULL,
    byte_size             BIGINT       NOT NULL,
    created_by            BINARY(16)   NOT NULL,
    upload_expires_at     DATETIME(6)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    ready_at              DATETIME(6)  NULL,
    PRIMARY KEY (media_id),
    CONSTRAINT uk_product_media_staging_key UNIQUE (staging_object_key),
    CONSTRAINT uk_product_media_published_key UNIQUE (published_object_key),
    CONSTRAINT fk_product_media_catalog FOREIGN KEY (catalog_id) REFERENCES catalog_revision (catalog_id),
    CONSTRAINT ck_product_media_status CHECK (status IN ('PENDING', 'READY', 'REJECTED')),
    CONSTRAINT ck_product_media_byte_size CHECK (byte_size >= 0),
    CONSTRAINT ck_product_media_ready_requires_published CHECK (
        status <> 'READY'
        OR (published_object_key IS NOT NULL AND object_etag IS NOT NULL AND ready_at IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 카테고리명과 Catalog 안의 표시 순서.
CREATE TABLE category
(
    category_id   BINARY(16)  NOT NULL,
    catalog_id    BINARY(16)  NOT NULL,
    name          VARCHAR(60) NOT NULL,
    display_order INT         NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT uk_category_display_order UNIQUE (catalog_id, display_order),
    CONSTRAINT fk_category_catalog FOREIGN KEY (catalog_id) REFERENCES catalog_revision (catalog_id),
    CONSTRAINT ck_category_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 상품 기본 정보, 가격, 대표 이미지 참조, 판매·품절·재고 관리 설정과 Category 안의 표시 순서.
CREATE TABLE product
(
    product_id     BINARY(16)    NOT NULL,
    catalog_id     BINARY(16)    NOT NULL,
    category_id    BINARY(16)    NOT NULL,
    media_id       BINARY(16)    NULL,
    name           VARCHAR(100)  NOT NULL,
    description    VARCHAR(2000) NULL,
    base_price     BIGINT        NOT NULL,
    image_alt_text VARCHAR(200)  NULL,
    sales_enabled  BOOLEAN       NOT NULL,
    sold_out       BOOLEAN       NOT NULL DEFAULT FALSE,
    stock_managed  BOOLEAN       NOT NULL,
    display_order  INT           NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT uk_product_display_order UNIQUE (category_id, display_order),
    CONSTRAINT fk_product_catalog FOREIGN KEY (catalog_id) REFERENCES catalog_revision (catalog_id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (category_id),
    CONSTRAINT fk_product_media FOREIGN KEY (media_id) REFERENCES product_media (media_id),
    CONSTRAINT ck_product_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_product_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Product Aggregate 안의 옵션명, 추가 금액, 활성 상태와 Product 안의 표시 순서.
CREATE TABLE product_option
(
    product_option_id BINARY(16)   NOT NULL,
    product_id         BINARY(16)   NOT NULL,
    name               VARCHAR(100) NOT NULL,
    additional_price   BIGINT       NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order      INT          NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (product_option_id),
    CONSTRAINT uk_product_option_display_order UNIQUE (product_id, display_order),
    CONSTRAINT fk_product_option_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT ck_product_option_additional_price CHECK (additional_price >= 0),
    CONSTRAINT ck_product_option_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
