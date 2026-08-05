package com.dorosoft.erp.table.application.port;

/**
 * TABLE-00/TABLE-10: table_idempotency_record.response_body에 저장되는 멱등 재생 응답을 원문이 DB에
 * 남지 않도록 봉투 암호화·복호화하는 경계.
 *
 * <p>구현체는 {@link com.dorosoft.erp.table.application.idempotency.TableIdempotencyCryptoUnavailableException}을
 * 던져 암호화 Key가 구성되지 않은 환경에서 멱등 저장 기능 자체를 안전하게 실패시키거나,
 * {@link com.dorosoft.erp.table.application.idempotency.TableIdempotencyCryptoIntegrityException}을 던져
 * 변조되었거나 잘못된 Key로는 복호화할 수 없는 저장값을 안전하게 거부할 수 있다.
 */
public interface TableIdempotencyResponseCrypto {

    /** 평문 JSON 응답을 암호화해 영속 계층에 저장 가능한 문자열 표현으로 변환한다. */
    String encrypt(String plaintext);

    /** {@link #encrypt(String)}로 저장된 문자열을 원래의 평문 JSON 응답으로 복호화한다. */
    String decrypt(String stored);
}
