package com.dorosoft.erp.catalog.domain.media;

import java.util.UUID;

/** Media 업로드·완료 흐름의 공통 예외. HTTP Controller가 구현되면 API 오류 코드와 1:1로 매핑한다. */
public abstract class MediaException extends RuntimeException {

    private final UUID mediaId;

    protected MediaException(UUID mediaId, String message) {
        super(message);
        this.mediaId = mediaId;
    }

    public UUID mediaId() {
        return mediaId;
    }
}
