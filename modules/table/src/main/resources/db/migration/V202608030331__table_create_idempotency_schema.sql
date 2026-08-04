-- TABLE-02 mutating API Idempotency-Key replay store.

CREATE TABLE table_idempotency_record
(
    idempotency_key VARCHAR(255) NOT NULL,
    request_method  VARCHAR(10)  NOT NULL,
    request_path    VARCHAR(255) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    response_status SMALLINT     NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
