package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/**
 * Public Key가 이미 존재하지만 기존 Object의 Checksum·크기·형식이 이번 완료 요청과 다르다.
 * 완료 서비스가 이 예외를 던지기 전에 Media를 REJECTED로 저장한다. API 오류 코드: 409 MEDIA_PUBLISH_CONFLICT.
 */
public class MediaPublishConflictException extends MediaException {

    public MediaPublishConflictException(UUID mediaId) {
        super(mediaId, "Public Object Metadata가 예상값과 다릅니다. mediaId=" + mediaId);
    }
}
