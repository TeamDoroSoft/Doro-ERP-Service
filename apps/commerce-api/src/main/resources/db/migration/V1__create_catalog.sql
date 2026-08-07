-- 03 상품·메뉴 관리 (FR-CATALOG-001 ~ FR-CATALOG-005)
-- Commerce Service가 소유한 commerce_db Schema다.
-- 삭제 대신 상태 비활성화를 사용하므로 물리 DELETE를 전제로 한 설계를 두지 않는다.

CREATE TABLE menu_category (
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_order INTEGER      NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_menu_category PRIMARY KEY (id),
    CONSTRAINT ck_menu_category_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_menu_category_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_menu_category_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_menu_category_version CHECK (version >= 0)
);

-- Tenant 안에서 Category 이름은 중복될 수 없다. 비활성 Category도 이름을 점유한다.
CREATE UNIQUE INDEX ux_menu_category_tenant_name ON menu_category (tenant_id, name);
CREATE INDEX ix_menu_category_tenant_order ON menu_category (tenant_id, display_order, id);

CREATE TABLE product (
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    category_id   UUID         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    price         BIGINT       NOT NULL,
    sold_out      BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)  NOT NULL,
    display_order INTEGER      NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES menu_category (id),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- 가격은 0 이상의 KRW 정수다. 음수는 Database에서도 저장할 수 없다.
    CONSTRAINT ck_product_price_not_negative CHECK (price >= 0),
    CONSTRAINT ck_product_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_product_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_product_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_product_tenant_name ON product (tenant_id, name);
CREATE INDEX ix_product_tenant_category_order ON product (tenant_id, category_id, display_order, id);
CREATE INDEX ix_product_tenant_status ON product (tenant_id, status);

-- 중앙 Audit 전달용 Outbox. 업무 변경과 같은 Local Transaction에 기록한다.
CREATE TABLE outbox_event (
    id              UUID         NOT NULL,
    event_id        UUID         NOT NULL,
    destination     VARCHAR(80)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    event_version   INTEGER      NOT NULL,
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    store_id        UUID,
    message_group   VARCHAR(120) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    lease_token     UUID,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    occurred_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT ux_outbox_event_id_destination UNIQUE (event_id, destination),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX ix_outbox_event_dispatch ON outbox_event (status, next_attempt_at, id);
CREATE INDEX ix_outbox_event_tenant ON outbox_event (tenant_id, occurred_at DESC);
