package com.dorosoft.erp.identity.infrastructure.persistence.crypto;

import com.dorosoft.erp.identity.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2idPasswordHasherTest {
    @Test
    void fixedArgon2idV1ParametersHashAndVerifyWithoutExposingThePassword() {
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String password = PasswordPolicy.normalizeAndValidate("correct horse 배터리 staple");
        String hash = hasher.hash(password);

        assertTrue(hash.startsWith("{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$"));
        assertFalse(hash.contains(password));
        assertTrue(hasher.matches(password, hash));
        assertFalse(hasher.matches(password + "x", hash));
        assertFalse(hasher.needsRehash(hash));
    }
}
