package com.dorosoft.erp.catalog.application.media;

import java.util.UUID;

public record RequestMediaUploadCommand(
        String contentType, long byteSize, String checksumSha256Base64, UUID requestedBy, String idempotencyKey) {}
