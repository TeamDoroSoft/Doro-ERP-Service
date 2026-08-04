package com.dorosoft.erp.catalog.domain.media;

import java.util.Arrays;
import java.util.Optional;

/** ADR-007이 허용한 상품 이미지 형식과 Public Key 확장자 매핑. SVG·HTML·실행 형식은 열거값 자체에 없어 거부된다. */
public enum MediaContentType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    MediaContentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<MediaContentType> fromMimeType(String mimeType) {
        return Arrays.stream(values()).filter(type -> type.mimeType.equalsIgnoreCase(mimeType)).findFirst();
    }

    public static boolean isAllowed(String mimeType) {
        return fromMimeType(mimeType).isPresent();
    }
}
