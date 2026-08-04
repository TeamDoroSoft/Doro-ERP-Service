CREATE TABLE IF NOT EXISTS audit_record (
    audit_id CHAR(36) NOT NULL,
    domain VARCHAR(30) NOT NULL,
    action VARCHAR(80) NOT NULL,
    operation_id CHAR(36) NOT NULL,
    event_sequence INT NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id CHAR(36) NULL,
    actor_role_snapshot VARCHAR(40) NOT NULL,
    actor_display_name_snapshot VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id CHAR(36) NOT NULL,
    before_value JSON NOT NULL,
    after_value JSON NOT NULL,
    reason_code VARCHAR(50) NULL,
    reason VARCHAR(500) NULL,
    value_schema_version INT NOT NULL,
    retention_class VARCHAR(40) NOT NULL,
    retention_until DATETIME(6) NOT NULL,
    payload_hmac BINARY(32) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_audit_record PRIMARY KEY (audit_id),
    CONSTRAINT uq_audit_record_operation UNIQUE KEY uq_audit_record_operation (domain, operation_id, event_sequence),
    CONSTRAINT chk_audit_record_event_sequence CHECK (event_sequence >= 0),
    CONSTRAINT chk_audit_record_value_schema_version CHECK (value_schema_version >= 1),
    CONSTRAINT chk_audit_record_before_object CHECK (JSON_TYPE(before_value) = 'OBJECT'),
    CONSTRAINT chk_audit_record_after_object CHECK (JSON_TYPE(after_value) = 'OBJECT'),
    CONSTRAINT chk_audit_record_actor CHECK (actor_type = 'SYSTEM' OR actor_id IS NOT NULL),
    CONSTRAINT chk_audit_record_recorded_after_occurred CHECK (recorded_at >= occurred_at),
    INDEX idx_audit_record_latest (occurred_at DESC, audit_id DESC),
    INDEX idx_audit_record_domain (domain, occurred_at DESC, audit_id DESC),
    INDEX idx_audit_record_action (action, occurred_at DESC, audit_id DESC),
    INDEX idx_audit_record_actor (actor_id, occurred_at DESC, audit_id DESC),
    INDEX idx_audit_record_request (request_id, occurred_at DESC),
    INDEX idx_audit_record_retention (retention_until, audit_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_record_target (
    audit_id CHAR(36) NOT NULL,
    relation_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id CHAR(36) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_audit_record_target PRIMARY KEY (audit_id, relation_type, target_type, target_id),
    CONSTRAINT fk_audit_record_target_audit_id FOREIGN KEY (audit_id)
        REFERENCES audit_record (audit_id) ON DELETE RESTRICT,
    INDEX idx_audit_target_type (target_type, occurred_at DESC, audit_id DESC),
    INDEX idx_audit_target_id (target_id, occurred_at DESC, audit_id DESC),
    INDEX idx_audit_target_type_id (target_type, target_id, occurred_at DESC, audit_id DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS privacy_access_log (
    access_log_id CHAR(36) NOT NULL,
    accessor_type VARCHAR(30) NOT NULL,
    accessor_id CHAR(36) NULL,
    accessor_role_snapshot VARCHAR(40) NOT NULL,
    purpose_code VARCHAR(50) NOT NULL,
    access_action VARCHAR(20) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    result_code VARCHAR(30) NOT NULL,
    result_count INT NOT NULL,
    client_address_ciphertext VARBINARY(512) NULL,
    client_address_key_version VARCHAR(50) NULL,
    request_id VARCHAR(100) NOT NULL,
    accessed_at DATETIME(6) NOT NULL,
    retention_until DATETIME(6) NOT NULL,
    CONSTRAINT pk_privacy_access_log PRIMARY KEY (access_log_id),
    CONSTRAINT chk_privacy_access_log_result_count CHECK (result_count >= 0),
    CONSTRAINT chk_privacy_access_log_address_key CHECK (
        (client_address_ciphertext IS NULL AND client_address_key_version IS NULL)
        OR (client_address_ciphertext IS NOT NULL AND client_address_key_version IS NOT NULL)
    ),
    INDEX idx_privacy_access_latest (accessed_at DESC, access_log_id DESC),
    INDEX idx_privacy_access_accessor (accessor_id, accessed_at DESC),
    INDEX idx_privacy_access_retention (retention_until, access_log_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS privacy_access_log_subject (
    access_log_id CHAR(36) NOT NULL,
    subject_type VARCHAR(30) NOT NULL,
    subject_id CHAR(36) NOT NULL,
    CONSTRAINT pk_privacy_access_log_subject PRIMARY KEY (access_log_id, subject_type, subject_id),
    CONSTRAINT fk_privacy_access_log_subject_log FOREIGN KEY (access_log_id)
        REFERENCES privacy_access_log (access_log_id) ON DELETE RESTRICT,
    INDEX idx_privacy_subject_lookup (subject_type, subject_id, access_log_id)
) ENGINE=InnoDB;
