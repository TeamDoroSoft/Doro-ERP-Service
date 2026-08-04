package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/** 현재 업체에 존재하지 않는 mediaId 요청. API 오류 코드: 404 MEDIA_NOT_FOUND. */
public class MediaNotFoundException extends MediaException {

    public MediaNotFoundException(UUID mediaId) {
        super(mediaId, "Media를 찾을 수 없습니다. mediaId=" + mediaId);
    }
}
