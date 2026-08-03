package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/**
 * Staging Object가 없거나 실제 Content-Type·크기·Checksum이 선언값과 다르다.
 * 완료 서비스가 이 예외를 던지기 전에 Media를 REJECTED로 저장한다. API 오류 코드: 400 INVALID_MEDIA_OBJECT.
 */
public class InvalidMediaObjectException extends MediaException {

    public InvalidMediaObjectException(UUID mediaId, String reason) {
        super(mediaId, "Media Object 검증에 실패했습니다. mediaId=" + mediaId + ", 사유=" + reason);
    }
}
