package com.dorosoft.erp.table.application.idempotency;

/**
 * 멱등 응답 암호화 Key가 구성되지 않아 멱등 저장·재생 기능을 사용할 수 없을 때 던지는 안전한 내부 신호.
 * 메시지에는 Key, 평문, IV, 인증 태그 등 민감값을 포함하지 않는다.
 */
public final class TableIdempotencyCryptoUnavailableException extends RuntimeException {
    public TableIdempotencyCryptoUnavailableException(String message) {
        super(message);
    }
}
