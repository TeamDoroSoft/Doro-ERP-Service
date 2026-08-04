package com.dorosoft.erp.identity.domain.credential;

import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {
    @Test
    void nfcNormalizationUsesUnicodeCodePointsWithoutTrimming() {
        String decomposed = "A" + "\u030a" + "a".repeat(14);
        String normalized = PasswordPolicy.normalizeAndValidate(decomposed);
        assertEquals(Normalizer.normalize(decomposed, Normalizer.Form.NFC), normalized);
        assertEquals("  abcdefghijklm  ", PasswordPolicy.normalizeAndValidate("  abcdefghijklm  "));
    }

    @Test
    void lengthAndVersionedBlocklistAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.normalizeAndValidate("short"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.normalizeAndValidate("a".repeat(129)));
        assertThrows(IllegalArgumentException.class, () ->
                PasswordPolicy.normalizeAndValidate("myPASSWORD1234Value")
        );
        assertEquals("2026-08-v1", VersionedPasswordBlocklist.defaultPolicy().version());
    }

    @Test
    void encodedHashMustMatchTheFixedArgon2idCost() {
        String valid = "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$ABCD$EFGH";
        String invalid = "{argon2id-v1}$argon2id$v=19$m=4096,t=3,p=1$ABCD$EFGH";
        PasswordPolicy.validateEncodedHash(valid);
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validateEncodedHash(invalid));
    }

    @Test
    void currentAndFourPreviousHashesAreComparedThroughTheHasher() {
        PasswordPolicy.PasswordHashMatcher matcher = (raw, hash) -> hash.equals("hash:" + raw);
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validateNotReused(
                "abcdefghijklmnop", "hash:abcdefghijklmnop", List.of(), matcher
        ));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validateNotReused(
                "abcdefghijklmnop", "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$BB",
                List.of("hash:abcdefghijklmnop"), matcher
        ));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validateNotReused(
                "abcdefghijklmnop", "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$BB",
                List.of(
                        "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$01",
                        "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$02",
                        "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$03",
                        "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$04",
                        "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$AA$05"
                ), matcher
        ));
    }
}
