package com.dorosoft.erp.catalog.application.port;

import java.util.Objects;
import java.util.Optional;

/**
 * 조건부 Public 승격(Source ETag 일치 + Target Key 부재) 결과.
 * SOURCE_CHANGED는 HEAD 이후 Staging이 바뀐 경우, TARGET_ALREADY_EXISTS는 Public Key가 이미 있는 경우다.
 * publicObjectKey는 Adapter가 조합한 값을 그대로 돌려주며 Application은 형식을 알 필요가 없다.
 */
public record PromotionOutcome(
        PromotionStatus status, String publicObjectKey, String publicEtag, S3ObjectMetadata existingPublicObject) {

    public enum PromotionStatus {
        PROMOTED,
        SOURCE_CHANGED,
        TARGET_ALREADY_EXISTS
    }

    public static PromotionOutcome promoted(String publicObjectKey, String publicEtag) {
        Objects.requireNonNull(publicObjectKey, "publicObjectKey는 필수다");
        Objects.requireNonNull(publicEtag, "publicEtag는 필수다");
        return new PromotionOutcome(PromotionStatus.PROMOTED, publicObjectKey, publicEtag, null);
    }

    public static PromotionOutcome sourceChanged() {
        return new PromotionOutcome(PromotionStatus.SOURCE_CHANGED, null, null, null);
    }

    public static PromotionOutcome targetAlreadyExists(String publicObjectKey, S3ObjectMetadata existingPublicObject) {
        Objects.requireNonNull(publicObjectKey, "publicObjectKey는 필수다");
        Objects.requireNonNull(existingPublicObject, "existingPublicObject는 필수다");
        return new PromotionOutcome(PromotionStatus.TARGET_ALREADY_EXISTS, publicObjectKey, null, existingPublicObject);
    }

    public Optional<S3ObjectMetadata> existingPublicObjectMetadata() {
        return Optional.ofNullable(existingPublicObject);
    }
}
