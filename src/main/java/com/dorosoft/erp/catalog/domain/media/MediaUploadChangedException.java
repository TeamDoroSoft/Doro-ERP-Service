package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/**
 * HEAD 검증 이후 Staging Object가 덮어써져 조건부 복사의 Source ETag가 달라졌다.
 * 완료 서비스가 이 예외를 던지기 전에 Media를 REJECTED로 저장한다. API 오류 코드: 409 MEDIA_UPLOAD_CHANGED.
 */
public class MediaUploadChangedException extends MediaException {

    public MediaUploadChangedException(UUID mediaId) {
        super(mediaId, "HEAD 검증 이후 Staging Object가 변경되었습니다. mediaId=" + mediaId);
    }
}
