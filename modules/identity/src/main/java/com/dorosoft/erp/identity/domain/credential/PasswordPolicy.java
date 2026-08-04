package com.dorosoft.erp.identity.domain.credential;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

/** 비밀번호 원문·저장 형식·최근 사용 이력을 검증하는 순수 Domain 정책. */
public final class PasswordPolicy {
    public static final int MIN_CODE_POINTS = 15;
    public static final int MAX_CODE_POINTS = 128;
    public static final int MAX_PREVIOUS_HASHES = 4;
    public static final String ALGORITHM_PREFIX = "{argon2id-v1}";

    private static final Pattern ENCODED_HASH_PATTERN = Pattern.compile(
            "\\A\\{argon2id-v1}\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]+={0,2}\\$[A-Za-z0-9+/]+={0,2}\\z"
    );

    private PasswordPolicy() {
    }

    public static String normalizeAndValidate(String rawPassword) {
        return normalizeAndValidate(rawPassword, VersionedPasswordBlocklist.defaultPolicy());
    }

    public static String normalizeAndValidate(
            String rawPassword,
            VersionedPasswordBlocklist blocklist
    ) {
        Objects.requireNonNull(rawPassword, "password");
        Objects.requireNonNull(blocklist, "blocklist");
        String normalized = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("password must be 15 to 128 Unicode code points");
        }
        if (blocklist.contains(normalized)) {
            throw new IllegalArgumentException("password is blocked by the local password policy");
        }
        return normalized;
    }

    public static void validateEncodedHash(String encodedHash) {
        Objects.requireNonNull(encodedHash, "encodedHash");
        if (!ENCODED_HASH_PATTERN.matcher(encodedHash).matches()) {
            throw new IllegalArgumentException("password hash must follow argon2id-v1 policy");
        }
    }

    public static void validateNotReused(
            String normalizedCandidate,
            String currentHash,
            Collection<String> previousHashes,
            PasswordHashMatcher matcher
    ) {
        Objects.requireNonNull(normalizedCandidate, "normalizedCandidate");
        Objects.requireNonNull(currentHash, "currentHash");
        Objects.requireNonNull(previousHashes, "previousHashes");
        Objects.requireNonNull(matcher, "matcher");
        if (previousHashes.size() > MAX_PREVIOUS_HASHES) {
            throw new IllegalArgumentException("at most four previous password hashes are allowed");
        }
        if (matcher.matches(normalizedCandidate, currentHash)) {
            throw new IllegalArgumentException("current password cannot be reused");
        }
        for (String previousHash : previousHashes) {
            validateEncodedHash(previousHash);
            if (matcher.matches(normalizedCandidate, previousHash)) {
                throw new IllegalArgumentException("recent password cannot be reused");
            }
        }
    }

    public static boolean requiresRehash(String encodedHash) {
        try {
            validateEncodedHash(encodedHash);
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    @FunctionalInterface
    public interface PasswordHashMatcher {
        boolean matches(CharSequence rawPassword, String encodedHash);
    }
}
