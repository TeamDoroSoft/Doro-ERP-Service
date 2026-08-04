package com.dorosoft.erp.table.application.dto;

import java.time.Instant;
import java.util.UUID;

public record QrCredentialReplayResponse(
        UUID credentialId,
        UUID tableId,
        UUID predecessorCredentialId,
        String status,
        Instant issuedAt) {}
