-- Identity domain foundation: employee accounts, kiosk devices, idempotency records and local security history.
-- tenant_id / store_id are logical references only (no FK): the owning tenant/store tables are not part of this
-- baseline yet. Do not add a foreign key here until that ownership is confirmed.

CREATE TABLE employee_account (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    store_id                        UUID NOT NULL,
    login_id                        VARCHAR(50) NOT NULL,
    password_hash                   VARCHAR(255) NOT NULL,
    role                            VARCHAR(20) NOT NULL,
    status                          VARCHAR(20) NOT NULL,
    password_change_required        BOOLEAN NOT NULL DEFAULT FALSE,
    temporary_password_expires_at   TIMESTAMPTZ,
    failed_login_count              INTEGER NOT NULL DEFAULT 0,
    last_failed_at                  TIMESTAMPTZ,
    lockout_level                   INTEGER NOT NULL DEFAULT 0,
    locked_until                    TIMESTAMPTZ,
    last_password_authenticated_at  TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_employee_account_tenant_login UNIQUE (tenant_id, login_id)
);

CREATE INDEX ix_employee_account_tenant_role_status
    ON employee_account (tenant_id, role, status);

CREATE TABLE kiosk_device (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    store_id             UUID NOT NULL,
    device_code          VARCHAR(100) NOT NULL,
    credential_id        VARCHAR(100) NOT NULL,
    secret_digest        VARCHAR(255) NOT NULL,
    credential_version   INTEGER NOT NULL DEFAULT 1,
    status               VARCHAR(20) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_kiosk_device_credential_id UNIQUE (credential_id),
    CONSTRAINT uq_kiosk_device_tenant_store_code UNIQUE (tenant_id, store_id, device_code)
);

CREATE TABLE identity_idempotency_record (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    actor_employee_id    UUID NOT NULL,
    operation            VARCHAR(50) NOT NULL,
    key_digest           VARCHAR(255) NOT NULL,
    request_hmac         VARCHAR(255) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    result_payload       TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    completed_at         TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_identity_idempotency_scope
        UNIQUE (tenant_id, actor_employee_id, operation, key_digest)
);

CREATE INDEX ix_identity_idempotency_expires_at
    ON identity_idempotency_record (expires_at);

CREATE TABLE employee_security_history (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    store_id             UUID NOT NULL,
    event_type           VARCHAR(50) NOT NULL,
    actor_employee_id    UUID,
    target_type          VARCHAR(50),
    target_id            UUID,
    result               VARCHAR(20) NOT NULL,
    reason_code          VARCHAR(50),
    previous_value       VARCHAR(50),
    new_value            VARCHAR(50),
    occurred_at          TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_employee_security_history_tenant_cursor
    ON employee_security_history (tenant_id, occurred_at DESC, id DESC);

CREATE INDEX ix_employee_security_history_expires_at
    ON employee_security_history (expires_at);
