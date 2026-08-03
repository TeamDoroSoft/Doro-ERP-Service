package com.dorosoft.erp.catalog.domain.product;

import java.util.UUID;

/** Product에 연결하려는 Media가 READY 상태가 아니다. API 오류 코드: 409 MEDIA_NOT_READY. */
public class MediaNotReadyException extends RuntimeException {

    public MediaNotReadyException(UUID mediaId) {
        super("Media가 READY 상태가 아닙니다. mediaId=" + mediaId);
    }
}
