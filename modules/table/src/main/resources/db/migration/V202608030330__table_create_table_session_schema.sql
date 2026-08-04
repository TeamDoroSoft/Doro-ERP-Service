-- 테이블 관리(Table) 모듈의 Store Table·QR Credential·Usage Session 기반 Schema를 생성한다.
-- Store는 물리 Table과 QR Credential을, Order는 Table Usage Session을 소유한다.

CREATE TABLE store_table
(
    table_id          BINARY(16)  NOT NULL,
    table_number      VARCHAR(20) NOT NULL,
    normalized_number VARCHAR(20) NOT NULL,
    display_name      VARCHAR(60) NOT NULL,
    seat_capacity     SMALLINT    NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (table_id),
    CONSTRAINT uk_store_table_normalized_number UNIQUE (normalized_number),
    CONSTRAINT ck_store_table_number_not_blank CHECK (CHAR_LENGTH(TRIM(table_number)) > 0),
    CONSTRAINT ck_store_table_normalized_number_not_blank CHECK (CHAR_LENGTH(TRIM(normalized_number)) > 0),
    CONSTRAINT ck_store_table_display_name_not_blank CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_store_table_seat_capacity CHECK (seat_capacity BETWEEN 1 AND 999)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE table_qr_credential
(
    credential_id   BINARY(16)  NOT NULL,
    table_id        BINARY(16)  NOT NULL,
    active_table_id BINARY(16) GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN table_id ELSE NULL END) STORED,
    token_digest    BINARY(32)  NOT NULL,
    status          VARCHAR(20) NOT NULL,
    predecessor_id  BINARY(16)  NULL,
    issued_by       BINARY(16)  NOT NULL,
    issued_at       DATETIME(6) NOT NULL,
    revoked_by      BINARY(16)  NULL,
    revoked_at      DATETIME(6) NULL,
    PRIMARY KEY (credential_id),
    CONSTRAINT uk_table_qr_credential_token_digest UNIQUE (token_digest),
    CONSTRAINT uk_active_qr_credential_per_table UNIQUE (active_table_id),
    CONSTRAINT fk_table_qr_credential_table FOREIGN KEY (table_id) REFERENCES store_table (table_id),
    CONSTRAINT fk_table_qr_credential_predecessor FOREIGN KEY (predecessor_id) REFERENCES table_qr_credential (credential_id),
    CONSTRAINT ck_table_qr_credential_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_table_qr_credential_revoke_state CHECK (
        (status = 'ACTIVE' AND revoked_by IS NULL AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_table_qr_credential_time_order CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE table_usage_session
(
    session_id    BINARY(16)  NOT NULL,
    table_id      BINARY(16)  NOT NULL,
    open_table_id BINARY(16) GENERATED ALWAYS AS
        (CASE WHEN status = 'OPEN' THEN table_id ELSE NULL END) STORED,
    status        VARCHAR(20) NOT NULL,
    opened_by     BINARY(16)  NOT NULL,
    opened_at     DATETIME(6) NOT NULL,
    closed_by     BINARY(16)  NULL,
    closed_at     DATETIME(6) NULL,
    close_reason  VARCHAR(40) NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id),
    CONSTRAINT uk_open_session_per_table UNIQUE (open_table_id),
    CONSTRAINT ck_table_usage_session_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_table_usage_session_close_state CHECK (
        (status = 'OPEN' AND closed_by IS NULL AND closed_at IS NULL AND close_reason IS NULL)
            OR (status = 'CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL
                AND close_reason IS NOT NULL AND CHAR_LENGTH(TRIM(close_reason)) > 0)
    ),
    CONSTRAINT ck_table_usage_session_time_order CHECK (closed_at IS NULL OR closed_at >= opened_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
