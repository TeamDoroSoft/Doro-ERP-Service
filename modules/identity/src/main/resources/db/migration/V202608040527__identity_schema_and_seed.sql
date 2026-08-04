CREATE TABLE employee_account (
    employee_account_id CHAR(36) NOT NULL,
    login_id_normalized VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    account_status VARCHAR(20) NOT NULL,
    login_lock_status VARCHAR(20) NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    temporary_lock_count INT NOT NULL DEFAULT 0,
    locked_at TIMESTAMP(6) NULL,
    locked_until TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_employee_account PRIMARY KEY (employee_account_id),
    CONSTRAINT uq_employee_account_login_id_normalized UNIQUE (login_id_normalized),
    CONSTRAINT chk_employee_account_status CHECK (account_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_employee_account_lock_status CHECK (login_lock_status IN ('NONE', 'TEMPORARY', 'PERMANENT')),
    CONSTRAINT chk_employee_account_failed_login_count CHECK (failed_login_count >= 0),
    CONSTRAINT chk_employee_account_temporary_lock_count CHECK (temporary_lock_count IN (0, 1)),
    CONSTRAINT chk_employee_account_lock_fields CHECK (
        (login_lock_status = 'NONE' AND locked_at IS NULL AND locked_until IS NULL)
        OR (
            login_lock_status = 'TEMPORARY'
            AND locked_at IS NOT NULL
            AND locked_until IS NOT NULL
            AND locked_until = TIMESTAMPADD(MINUTE, 5, locked_at)
        )
        OR (login_lock_status = 'PERMANENT' AND locked_at IS NOT NULL AND locked_until IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE credential (
    employee_account_id CHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 0,
    password_changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_credential PRIMARY KEY (employee_account_id),
    CONSTRAINT fk_credential_employee_account FOREIGN KEY (employee_account_id)
        REFERENCES employee_account (employee_account_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_credential_password_hash_algorithm CHECK (
        password_hash LIKE '{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$%'
    )
) ENGINE=InnoDB;

CREATE TABLE credential_password_history (
    password_history_id CHAR(36) NOT NULL,
    employee_account_id CHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_credential_password_history PRIMARY KEY (password_history_id),
    CONSTRAINT fk_credential_password_history_employee_account FOREIGN KEY (employee_account_id)
        REFERENCES employee_account (employee_account_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_credential_password_history_hash_algorithm CHECK (
        password_hash LIKE '{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$%'
    ),
    INDEX idx_credential_password_history_account_changed_at (employee_account_id, changed_at DESC)
) ENGINE=InnoDB;

CREATE TABLE role (
    role_id CHAR(36) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_role PRIMARY KEY (role_id),
    CONSTRAINT uq_role_code UNIQUE (code),
    CONSTRAINT chk_role_code_length CHECK (CHAR_LENGTH(code) BETWEEN 1 AND 40)
) ENGINE=InnoDB;

CREATE TABLE permission (
    permission_id CHAR(36) NOT NULL,
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT pk_permission PRIMARY KEY (permission_id),
    CONSTRAINT uq_permission_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE employee_role (
    employee_account_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    assigned_by CHAR(36) NULL,
    assignment_source VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_employee_role PRIMARY KEY (employee_account_id),
    CONSTRAINT fk_employee_role_account FOREIGN KEY (employee_account_id)
        REFERENCES employee_account (employee_account_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_employee_role_role FOREIGN KEY (role_id)
        REFERENCES role (role_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_employee_role_assignment_source CHECK (assignment_source IN ('BOOTSTRAP', 'ADMIN')),
    CONSTRAINT chk_employee_role_assignment_reference CHECK (
        (assignment_source = 'BOOTSTRAP' AND assigned_by IS NULL)
        OR (assignment_source = 'ADMIN' AND assigned_by IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE role_permission (
    role_id CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES role (role_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES permission (permission_id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE identity_idempotency_record (
    idempotency_record_id CHAR(36) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    key_digest BINARY(32) NOT NULL,
    request_hmac BINARY(32) NOT NULL,
    requested_by CHAR(36) NOT NULL,
    target_account_id CHAR(36) NULL,
    status VARCHAR(20) NOT NULL,
    http_status SMALLINT NULL,
    response_ciphertext BLOB NULL,
    response_nonce BINARY(12) NULL,
    master_key_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_identity_idempotency_record PRIMARY KEY (idempotency_record_id),
    CONSTRAINT uq_identity_idempotency_operation_key_digest UNIQUE (operation, key_digest),
    CONSTRAINT chk_identity_idempotency_operation CHECK (
        operation IN ('EMPLOYEE_ACCOUNT_CREATE', 'EMPLOYEE_PASSWORD_RESET')
    ),
    CONSTRAINT chk_identity_idempotency_status CHECK (status IN ('PROCESSING', 'SUCCEEDED')),
    CONSTRAINT chk_identity_idempotency_key_lengths CHECK (
        OCTET_LENGTH(key_digest) = 32 AND OCTET_LENGTH(request_hmac) = 32
    ),
    CONSTRAINT chk_identity_idempotency_master_key_version CHECK (
        CHAR_LENGTH(master_key_version) BETWEEN 1 AND 50
    ),
    CONSTRAINT chk_identity_idempotency_expiry CHECK (
        expires_at = TIMESTAMPADD(HOUR, 24, created_at)
    ),
    CONSTRAINT chk_identity_idempotency_result CHECK (
        (
            status = 'PROCESSING'
            AND target_account_id IS NULL
            AND http_status IS NULL
            AND response_ciphertext IS NULL
            AND response_nonce IS NULL
            AND completed_at IS NULL
        )
        OR (
            status = 'SUCCEEDED'
            AND target_account_id IS NOT NULL
            AND http_status IS NOT NULL
            AND completed_at IS NOT NULL
            AND (
                (http_status = 201 AND response_ciphertext IS NOT NULL AND response_nonce IS NOT NULL)
                OR (http_status = 204 AND response_ciphertext IS NULL AND response_nonce IS NULL)
            )
        )
    )
) ENGINE=InnoDB;

CREATE TABLE identity_security_event (
    security_event_id CHAR(36) NOT NULL,
    account_id CHAR(36) NULL,
    event_type VARCHAR(60) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    failure_class VARCHAR(60) NULL,
    request_id VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_identity_security_event PRIMARY KEY (security_event_id),
    CONSTRAINT fk_identity_security_event_account FOREIGN KEY (account_id)
        REFERENCES employee_account (employee_account_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_identity_security_event_type CHECK (event_type IN (
        'LOGIN_SUCCEEDED',
        'LOGIN_FAILED',
        'LOGIN_REJECTED',
        'LOGIN_RATE_LIMITED',
        'ACCOUNT_TEMPORARILY_LOCKED',
        'ACCOUNT_PERMANENTLY_LOCKED',
        'LOGIN_SESSION_CREATE_FAILED',
        'LOGOUT_SUCCEEDED',
        'PASSWORD_CHANGED',
        'PASSWORD_RESET'
    )),
    CONSTRAINT chk_identity_security_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT chk_identity_security_event_combination CHECK (
        (event_type = 'LOGIN_SUCCEEDED' AND outcome = 'SUCCESS' AND failure_class IS NULL AND account_id IS NOT NULL)
        OR (
            event_type = 'LOGIN_FAILED'
            AND outcome = 'FAILURE'
            AND failure_class IS NOT NULL
            AND failure_class = 'CREDENTIAL_MISMATCH'
        )
        OR (
            event_type = 'LOGIN_REJECTED'
            AND outcome = 'DENIED'
            AND failure_class IS NOT NULL
            AND failure_class IN ('ACCOUNT_INACTIVE', 'TEMPORARY_LOCK_ACTIVE', 'PERMANENT_LOCK_ACTIVE')
            AND account_id IS NOT NULL
        )
        OR (event_type = 'LOGIN_RATE_LIMITED' AND outcome = 'DENIED' AND failure_class IS NULL AND account_id IS NULL)
        OR (
            event_type IN (
                'ACCOUNT_TEMPORARILY_LOCKED',
                'ACCOUNT_PERMANENTLY_LOCKED',
                'LOGOUT_SUCCEEDED',
                'PASSWORD_CHANGED',
                'PASSWORD_RESET'
            )
            AND outcome = 'SUCCESS'
            AND failure_class IS NULL
            AND account_id IS NOT NULL
        )
        OR (
            event_type = 'LOGIN_SESSION_CREATE_FAILED'
            AND outcome = 'FAILURE'
            AND failure_class IS NOT NULL
            AND failure_class = 'SESSION_STORE_UNAVAILABLE'
            AND account_id IS NOT NULL
        )
    ),
    INDEX idx_identity_security_event_account_occurred_at (account_id, occurred_at DESC),
    INDEX idx_identity_security_event_occurred_at (occurred_at DESC, security_event_id DESC)
) ENGINE=InnoDB;

INSERT INTO permission (permission_id, code, description)
VALUES
    (UUID(), 'identity.account.read', '직원 목록·상세 조회'),
    (UUID(), 'identity.account.create', '직원 계정 발급'),
    (UUID(), 'identity.account.status.update', '계정 활성화·비활성화'),
    (UUID(), 'identity.account.unlock', '임시·영구 로그인 잠금 해제'),
    (UUID(), 'identity.credential.reset', '직원 비밀번호 초기화'),
    (UUID(), 'identity.role.read', '역할 목록 조회'),
    (UUID(), 'identity.role.assign', '직원 단일 역할 변경'),
    (UUID(), 'identity.role.update', '역할의 기능별 권한 변경'),
    (UUID(), 'identity.audit.read', 'Identity 감사 이력 조회'),
    (UUID(), 'audit.read', '중앙 감사 이력 목록·상세 조회'),
    (UUID(), 'privacy.access-log.read', '개인정보 접근기록 점검·이상 접근 조회'),
    (UUID(), 'store.settings.read', '매장 기본 정보·일정·기능 설정 조회'),
    (UUID(), 'store.settings.update', '매장 기본 정보·일정·기능 설정 변경'),
    (UUID(), 'catalog.read', '관리자·직원용 전체 카탈로그 조회'),
    (UUID(), 'catalog.manage', '카테고리·상품·옵션·가격·판매·재고 관리 설정 변경'),
    (UUID(), 'catalog.soldout.update', '상품 수동 품절 설정·해제'),
    (UUID(), 'table.read', '테이블 목록·상세 조회'),
    (UUID(), 'table.manage', '테이블 등록·변경·활성화와 QR 발급·재발급'),
    (UUID(), 'table.session.manage', '테이블 이용 Session 시작·종료'),
    (UUID(), 'table.session.move', '열린 테이블 이용 Session 전체 이동'),
    (UUID(), 'table.order.read', '테이블별 현재·과거 주문 조회'),
    (UUID(), 'table.layout.read', '그래픽 테이블 배치도·표시 정보·이용 상태 조회'),
    (UUID(), 'table.layout.manage', '테이블 배치·좌표·크기·모양·회전과 Canvas 저장'),
    (UUID(), 'table.cleaning.manage', '정리 중인 테이블의 정리 완료 처리'),
    (UUID(), 'order.create', '직원 직접 주문 생성'),
    (UUID(), 'order.read', '주문 목록·상세·영수증 조회'),
    (UUID(), 'order.status.update', '허용된 주문 처리 상태 변경'),
    (UUID(), 'order.cancel', '정책이 허용한 주문 전체 취소'),
    (UUID(), 'order.history.read', '주문 변경 이력 조회'),
    (UUID(), 'pickup.contact.read', '픽업 주문 연락처 원문 조회'),
    (UUID(), 'payment.read', '결제 목록·상세와 조정 요약 조회'),
    (UUID(), 'payment.event.read', '결제 상태·감사 Event 조회'),
    (UUID(), 'payment.recovery.read', '실패·재시도 대기 결제 작업 조회'),
    (UUID(), 'payment.recovery.manage', '실패한 결제 작업 수동 재개'),
    (UUID(), 'payment.manual.complete', '직원 주문의 현장 수기 결제 완료 기록'),
    (UUID(), 'payment.manual.correct', '수기 결제수단의 추가 전용 정정'),
    (UUID(), 'inventory.read', '현재고·부족 상태 조회'),
    (UUID(), 'inventory.initialize', '최초 재고 초기화'),
    (UUID(), 'inventory.receive', '상품 입고 등록'),
    (UUID(), 'inventory.adjust', '실사 수량 기반 수동 조정'),
    (UUID(), 'inventory.safety-stock.manage', '안전 재고 설정'),
    (UUID(), 'inventory.history.read', '재고 변경 원장 조회'),
    (UUID(), 'inventory.alert.read', '재고 부족 Alert 조회'),
    (UUID(), 'sales.read', '매출 Dashboard·분석 조회'),
    (UUID(), 'sales.closing.read', '마감 목록·상세·이력 조회'),
    (UUID(), 'sales.close', '일일 마감 실행'),
    (UUID(), 'sales.correct', '마감 후 승인 정정·재시도'),
    (UUID(), 'waiting.read', '웨이팅 목록·상세 조회'),
    (UUID(), 'waiting.call', '웨이팅 고객 입장 호출'),
    (UUID(), 'waiting.admit', '호출 고객 입장 완료'),
    (UUID(), 'waiting.cancel', '활성 웨이팅 직원 취소'),
    (UUID(), 'waiting.no_show', '호출 고객 노쇼 처리'),
    (UUID(), 'waiting.notification.resend', '호출 알림 수동 재발송'),
    (UUID(), 'reservation.read', '예약 목록·상세 조회'),
    (UUID(), 'reservation.decide', '예약 승인·거절'),
    (UUID(), 'reservation.update', '활성 예약 직접 변경'),
    (UUID(), 'reservation.cancel', '활성 예약 직원 취소'),
    (UUID(), 'reservation.visit.update', '예약 착석·완료 처리'),
    (UUID(), 'reservation.no_show', '예약 No-show 처리'),
    (UUID(), 'reservation.change.decide', '고객 변경 요청 승인·거절'),
    (UUID(), 'reservation.policy.read', '예약 슬롯·수용량 정책 조회'),
    (UUID(), 'reservation.policy.manage', '예약 슬롯·수용량 정책 전체 교체'),
    (UUID(), 'notification.read', '알림 발송 목록·상세 조회')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role (role_id, code, name, active, version)
VALUES
    (UUID(), 'ADMIN', '관리자', TRUE, 0),
    (UUID(), 'EMPLOYEE', '직원', TRUE, 0),
    (UUID(), 'INVENTORY_OPERATOR', '재고 운영자', TRUE, 0),
    (UUID(), 'RESERVATION_OPERATOR', '예약 운영자', TRUE, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), active = VALUES(active);

DELETE rp
FROM role_permission rp
JOIN role r ON r.role_id = rp.role_id
WHERE r.code IN ('ADMIN', 'EMPLOYEE', 'INVENTORY_OPERATOR', 'RESERVATION_OPERATOR');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.code IN (
    'table.read',
    'table.session.manage',
    'table.order.read',
    'order.create',
    'order.read',
    'order.status.update',
    'inventory.read',
    'waiting.read',
    'waiting.call',
    'waiting.admit',
    'waiting.cancel',
    'waiting.no_show',
    'reservation.read',
    'reservation.visit.update'
)
WHERE r.code = 'EMPLOYEE';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.code IN (
    'table.read',
    'table.session.manage',
    'table.order.read',
    'order.create',
    'order.read',
    'order.status.update',
    'inventory.read',
    'waiting.read',
    'waiting.call',
    'waiting.admit',
    'waiting.cancel',
    'waiting.no_show',
    'reservation.read',
    'reservation.visit.update',
    'inventory.receive',
    'inventory.alert.read'
)
WHERE r.code = 'INVENTORY_OPERATOR';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.code IN (
    'table.read',
    'table.session.manage',
    'table.order.read',
    'order.create',
    'order.read',
    'order.status.update',
    'inventory.read',
    'waiting.read',
    'waiting.call',
    'waiting.admit',
    'waiting.cancel',
    'waiting.no_show',
    'reservation.read',
    'reservation.visit.update',
    'reservation.decide',
    'reservation.update',
    'reservation.cancel',
    'reservation.no_show',
    'reservation.change.decide',
    'reservation.policy.read'
)
WHERE r.code = 'RESERVATION_OPERATOR';
