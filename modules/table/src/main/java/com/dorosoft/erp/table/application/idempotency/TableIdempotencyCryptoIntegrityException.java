package com.dorosoft.erp.table.application.idempotency;

/**
 * 저장된 멱등 응답 암호문이 변조되었거나, 형식이 손상되었거나, 다른 Key로 암호화되어 현재 Key로는
 * 복호화·인증 태그 검증에 실패했을 때 던지는 안전한 내부 신호. 메시지에는 Key, 평문, 암호문, IV,
 * 인증 태그 등 민감값이나 원인 예외 메시지를 포함하지 않는다.
 */
public final class TableIdempotencyCryptoIntegrityException extends RuntimeException {
    public TableIdempotencyCryptoIntegrityException(String message) {
        super(message);
    }
}
