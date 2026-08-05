package com.dorosoft.erp.table.application.idempotency;

import com.dorosoft.erp.table.application.port.TableIdempotencyResponseCrypto;

/**
 * 멱등 응답 암호화 Key가 구성되지 않은 환경(예: Key 미설정 개발 환경)에서 사용되는 Fail-closed Adapter.
 * 평문을 그대로 저장하는 방식으로 조용히 대체하지 않고, 실제 사용 시점에 항상 실패한다.
 */
public final class UnavailableTableIdempotencyResponseCrypto implements TableIdempotencyResponseCrypto {

    @Override
    public String encrypt(String plaintext) {
        throw unavailable();
    }

    @Override
    public String decrypt(String stored) {
        throw unavailable();
    }

    private TableIdempotencyCryptoUnavailableException unavailable() {
        return new TableIdempotencyCryptoUnavailableException(
                "Table idempotency response encryption key is unavailable.");
    }
}
