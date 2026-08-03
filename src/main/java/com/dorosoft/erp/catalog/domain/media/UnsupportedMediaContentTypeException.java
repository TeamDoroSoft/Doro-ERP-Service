package com.dorosoft.erp.catalog.domain.media;

/**
 * 업로드 준비 요청의 Content-Type이 JPEG·PNG·WebP가 아니다(SVG·HTML·실행 형식 포함).
 * Media Row를 만들기 전에 거부하므로 mediaId가 없다. API 오류 코드: 400 INVALID_MEDIA_OBJECT.
 */
public class UnsupportedMediaContentTypeException extends RuntimeException {

    public UnsupportedMediaContentTypeException(String contentType) {
        super("허용되지 않는 Content-Type입니다: " + contentType);
    }
}
