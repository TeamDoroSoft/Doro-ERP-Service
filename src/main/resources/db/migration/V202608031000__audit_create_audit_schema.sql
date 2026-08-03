-- 이 스키마의 operation_id/target_id/actor_id/value_schema_version 컬럼 타입은 데이터 모델 문서(UUID/INTEGER)가 아니라 이미 확정된 Java 계약(AuditRecordCommand/AuditContext/AuditTarget, String 타입)을 기준으로 채택했다. Docs 데이터 모델 문서 정정은 별도 후속 작업이다.

-- 변경 행위의 감사 원문과 위변조 검증 정보를 저장한다.
CREATE TABLE audit_record
(
    audit_id                   BINARY(16)   NOT NULL,
    domain                     VARCHAR(30)  NOT NULL,
    action                     VARCHAR(80)  NOT NULL,
    operation_id               VARCHAR(64)  NOT NULL,
    event_sequence             INTEGER      NOT NULL,
    actor_type                 VARCHAR(30)  NOT NULL,
    actor_id                   VARCHAR(100) NOT NULL,
    actor_role_snapshot        VARCHAR(40)  NULL,
    actor_display_name_snapshot VARCHAR(100) NULL,
    target_type                VARCHAR(50)  NOT NULL,
    target_id                  VARCHAR(100) NOT NULL,
    before_value               JSON         NOT NULL,
    after_value                JSON         NOT NULL,
    reason_code                VARCHAR(50)  NULL,
    reason                     VARCHAR(500) NULL,
    value_schema_version       VARCHAR(20)  NOT NULL,
    retention_class            VARCHAR(40)  NOT NULL,
    retention_until            TIMESTAMP(6) NOT NULL,
    payload_hmac               BINARY(32)   NOT NULL,
    request_id                 VARCHAR(100) NOT NULL,
    occurred_at                TIMESTAMP(6) NOT NULL,
    recorded_at                TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (audit_id),
    CONSTRAINT uk_audit_record_operation_sequence UNIQUE (domain, operation_id, event_sequence),
    CONSTRAINT ck_audit_record_event_sequence CHECK (event_sequence >= 0),
    CONSTRAINT ck_audit_record_before_value_object CHECK (JSON_TYPE(before_value) = 'OBJECT'),
    CONSTRAINT ck_audit_record_after_value_object CHECK (JSON_TYPE(after_value) = 'OBJECT'),
    INDEX ix_audit_record_domain_occurred (domain, occurred_at DESC, audit_id DESC),
    INDEX ix_audit_record_request_occurred (request_id, occurred_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 한 감사 기록에서 함께 조회할 기본·관련 대상을 저장한다.
CREATE TABLE audit_record_target
(
    audit_id      BINARY(16)   NOT NULL,
    relation_type VARCHAR(30)  NOT NULL,
    target_type   VARCHAR(50)  NOT NULL,
    target_id     VARCHAR(100) NOT NULL,
    occurred_at   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (audit_id, relation_type, target_type, target_id),
    CONSTRAINT fk_audit_record_target_record FOREIGN KEY (audit_id) REFERENCES audit_record (audit_id)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
