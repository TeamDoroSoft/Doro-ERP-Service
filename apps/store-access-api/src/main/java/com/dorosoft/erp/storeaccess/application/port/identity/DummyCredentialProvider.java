package com.dorosoft.erp.storeaccess.application.port.identity;

/**
 * A single Argon2id hash, generated once, used to run the same verification cost for a nonexistent
 * {@code loginId} as for a real account so login response timing does not reveal account existence
 * (ADR-02-006, ADR-02-009).
 */
public interface DummyCredentialProvider {

    String dummyPasswordHash();
}
