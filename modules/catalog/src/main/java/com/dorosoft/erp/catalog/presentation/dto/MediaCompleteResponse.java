package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.UUID;

public record MediaCompleteResponse(UUID mediaId, String status, String checksumSha256, Instant readyAt) {

    public static MediaCompleteResponse from(ProductMedia media) {
        return new MediaCompleteResponse(media.mediaId(), media.status().name(), media.checksumSha256(), media.readyAt());
    }
}
