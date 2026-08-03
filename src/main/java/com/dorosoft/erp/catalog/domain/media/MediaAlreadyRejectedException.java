package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/**
 * 만료 또는 이전 검증 실패로 이미 REJECTED인 Media에 다시 완료를 요청했다.
 * API 오류 코드: 409 MEDIA_UPLOAD_REJECTED.
 */
public class MediaAlreadyRejectedException extends MediaException {

    public MediaAlreadyRejectedException(UUID mediaId) {
        super(mediaId, "이미 거부된 Media입니다. mediaId=" + mediaId);
    }
}
