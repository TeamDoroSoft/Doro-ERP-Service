package com.dorosoft.erp.identity.infrastructure.persistence.crypto;

import com.dorosoft.erp.identity.application.port.PasswordHasher;
import com.dorosoft.erp.identity.domain.credential.PasswordPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** 확정된 Argon2id-v1 비용 Parameter를 사용하는 PasswordHasher. */
public final class Argon2idPasswordHasher implements PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder delegate = new Argon2PasswordEncoder(
            SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS
    );

    @Override
    public String hash(CharSequence normalizedPassword) {
        String encoded = PasswordPolicy.ALGORITHM_PREFIX + delegate.encode(normalizedPassword);
        PasswordPolicy.validateEncodedHash(encoded);
        return encoded;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith(PasswordPolicy.ALGORITHM_PREFIX)) {
            return false;
        }
        return delegate.matches(rawPassword, encodedHash.substring(PasswordPolicy.ALGORITHM_PREFIX.length()));
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith(PasswordPolicy.ALGORITHM_PREFIX)) {
            return true;
        }
        return delegate.upgradeEncoding(encodedHash.substring(PasswordPolicy.ALGORITHM_PREFIX.length()));
    }
}
