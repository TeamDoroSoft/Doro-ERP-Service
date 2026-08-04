package com.dorosoft.erp.catalog.application.media;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MediaUploadTicket(
        UUID mediaId, String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {}
