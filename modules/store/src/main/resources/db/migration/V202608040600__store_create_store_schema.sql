-- 매장 설정(Store) 모듈의 최초 스키마를 생성하는 Migration이다.
-- 매장 기본 정보와 영업시간, 정기/임시 휴무일, 서비스 운영시간, 기능 토글, 알림 이벤트 설정을 다룬다.
-- 초기 데이터(INSERT)는 넣지 않으며, 기본값 적재는 애플리케이션 Bootstrap이 담당한다.

-- 매장 기본 정보 (한 업체 = 한 매장, 단일 행 운영)
CREATE TABLE store_profile
(
    store_id   BINARY(16)   NOT NULL,
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    contact    VARCHAR(50)  NOT NULL,
    time_zone  VARCHAR(50)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (store_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 요일별 영업시간 구간 (end < start 는 자정 초과 구간이므로 허용, 같은 값만 금지)
CREATE TABLE business_hour
(
    business_hour_id BINARY(16) NOT NULL,
    store_id         BINARY(16) NOT NULL,
    day_of_week      SMALLINT   NOT NULL,
    period_order     SMALLINT   NOT NULL,
    start_local_time TIME       NOT NULL,
    end_local_time   TIME       NOT NULL,
    PRIMARY KEY (business_hour_id),
    CONSTRAINT uk_business_hour_slot UNIQUE (store_id, day_of_week, period_order),
    CONSTRAINT fk_business_hour_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id),
    CONSTRAINT ck_business_hour_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hour_period_order CHECK (period_order >= 0),
    CONSTRAINT ck_business_hour_time_range CHECK (start_local_time <> end_local_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 정기 휴무일 (요일 단위)
CREATE TABLE regular_closed_day
(
    store_id    BINARY(16) NOT NULL,
    day_of_week SMALLINT   NOT NULL,
    PRIMARY KEY (store_id, day_of_week),
    CONSTRAINT fk_regular_closed_day_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id),
    CONSTRAINT ck_regular_closed_day_day_of_week CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 임시 휴무일 (특정 날짜 단위)
CREATE TABLE temporary_closure
(
    temporary_closure_id BINARY(16)   NOT NULL,
    store_id             BINARY(16)   NOT NULL,
    closure_date         DATE         NOT NULL,
    reason               VARCHAR(255) NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (temporary_closure_id),
    CONSTRAINT uk_temporary_closure_date UNIQUE (store_id, closure_date),
    CONSTRAINT fk_temporary_closure_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 서비스별(주문/예약) 운영시간 구간
CREATE TABLE service_window
(
    service_window_id BINARY(16)  NOT NULL,
    store_id          BINARY(16)  NOT NULL,
    service_type      VARCHAR(20) NOT NULL,
    day_of_week       SMALLINT    NOT NULL,
    period_order      SMALLINT    NOT NULL,
    start_local_time  TIME        NOT NULL,
    end_local_time    TIME        NOT NULL,
    PRIMARY KEY (service_window_id),
    CONSTRAINT uk_service_window_slot UNIQUE (store_id, service_type, day_of_week, period_order),
    CONSTRAINT fk_service_window_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id),
    CONSTRAINT ck_service_window_service_type CHECK (service_type IN ('ORDER', 'RESERVATION')),
    CONSTRAINT ck_service_window_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_service_window_period_order CHECK (period_order >= 0),
    CONSTRAINT ck_service_window_time_range CHECK (start_local_time <> end_local_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 매장 기능 사용 여부 토글
CREATE TABLE feature_setting
(
    store_id     BINARY(16)  NOT NULL,
    feature_code VARCHAR(40) NOT NULL,
    enabled      BOOLEAN     NOT NULL,
    PRIMARY KEY (store_id, feature_code),
    CONSTRAINT fk_feature_setting_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id),
    CONSTRAINT ck_feature_setting_code CHECK (feature_code IN
                                              ('WAITING', 'RESERVATION', 'QR_ORDER', 'PICKUP_ORDER'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 알림 이벤트별 발송 여부 설정
CREATE TABLE notification_event_setting
(
    store_id   BINARY(16)  NOT NULL,
    event_code VARCHAR(60) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    PRIMARY KEY (store_id, event_code),
    CONSTRAINT fk_notification_event_setting_store FOREIGN KEY (store_id) REFERENCES store_profile (store_id),
    CONSTRAINT ck_notification_event_setting_code CHECK (event_code IN
                                                         ('WAITING_REGISTERED',
                                                          'WAITING_CALLED',
                                                          'RESERVATION_REQUESTED',
                                                          'RESERVATION_APPROVED',
                                                          'RESERVATION_REJECTED',
                                                          'RESERVATION_CHANGED',
                                                          'RESERVATION_CHANGE_REJECTED',
                                                          'RESERVATION_CANCELLED',
                                                          'RESERVATION_REMINDER',
                                                          'PICKUP_ORDER_RECEIVED',
                                                          'PICKUP_READY',
                                                          'PAYMENT_COMPLETED',
                                                          'PAYMENT_CANCELLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
