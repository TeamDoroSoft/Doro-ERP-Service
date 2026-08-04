package com.dorosoft.erp.identity.domain.credential;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 배포물과 함께 버전이 고정되는 로컬 비밀번호 차단 목록. */
public record VersionedPasswordBlocklist(String version, Set<String> normalizedEntries) {
    private static final VersionedPasswordBlocklist DEFAULT = new VersionedPasswordBlocklist(
            "2026-08-v1",
            Set.of("password1234", "1234567890abcdef", "qwerasdfzxcv")
    );

    public VersionedPasswordBlocklist {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(normalizedEntries, "normalizedEntries");
        if (version.isBlank()) {
            throw new IllegalArgumentException("blocklist version must not be blank");
        }
        normalizedEntries = normalizedEntries.stream()
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static VersionedPasswordBlocklist defaultPolicy() {
        return DEFAULT;
    }

    public boolean contains(String normalizedPassword) {
        String folded = normalizedPassword.toLowerCase(Locale.ROOT);
        return normalizedEntries.stream().anyMatch(folded::contains);
    }
}
