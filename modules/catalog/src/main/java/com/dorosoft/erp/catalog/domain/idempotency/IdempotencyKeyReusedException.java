package com.dorosoft.erp.catalog.domain.idempotency;

/** 같은 Idempotency-Key를 다른 요청 내용으로 재사용했다. API 오류 코드: 409 IDEMPOTENCY_KEY_REUSED. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency-Key가 다른 요청 내용으로 재사용되었습니다. key=" + idempotencyKey);
    }
}
