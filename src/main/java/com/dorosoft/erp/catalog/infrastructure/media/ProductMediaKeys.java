package com.dorosoft.erp.catalog.infrastructure.media;

import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.Sha256Checksums;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** ADR-007이 정한 Staging·Public Object Key 형식. Infrastructure Adapter 전용이다. */
final class ProductMediaKeys {

    private ProductMediaKeys() {}

    static String stagingKey(String tenantId, UUID mediaId) {
        return "tenants/%s/catalog/staging/%s/source".formatted(requireTenantId(tenantId), mediaId);
    }

    static String publicKey(String tenantId, UUID mediaId, String checksumSha256Base64, MediaContentType contentType) {
        Objects.requireNonNull(contentType, "contentType은 필수다");
        String sha256Hex = toLowerCaseHex(checksumSha256Base64);
        return "tenants/%s/catalog/public/%s/%s.%s"
                .formatted(requireTenantId(tenantId), mediaId, sha256Hex, contentType.extension());
    }

    /** 검증된 Base64 SHA-256을 소문자 16진수로 변환한다(ADR-007). */
    static String toLowerCaseHex(String checksumSha256Base64) {
        Sha256Checksums.requireValidBase64(checksumSha256Base64);
        byte[] decoded = Base64.getDecoder().decode(checksumSha256Base64);
        return HexFormat.of().formatHex(decoded);
    }

    private static String requireTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId는 필수다");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId는 공백일 수 없다");
        }
        return tenantId;
    }
}
