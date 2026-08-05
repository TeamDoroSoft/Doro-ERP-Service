package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.application.media.MediaUploadTicket;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MediaUploadTicketResponse(UUID mediaId, String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {

    public static MediaUploadTicketResponse from(MediaUploadTicket ticket) {
        return new MediaUploadTicketResponse(
                ticket.mediaId(), ticket.uploadUrl(), ticket.requiredHeaders(), ticket.expiresAt());
    }
}
