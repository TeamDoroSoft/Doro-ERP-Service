package com.dorosoft.erp.table.application.dto;

import java.time.Instant;
import java.util.UUID;

public record QrCredentialIssueResponse(
        UUID credentialId,
        UUID tableId,
        UUID predecessorCredentialId,
        String status,
        Instant issuedAt,
        String accessUrl) {

    public QrCredentialReplayResponse replayWithoutSecret() {
        return new QrCredentialReplayResponse(
                credentialId, tableId, predecessorCredentialId, status, issuedAt);
    }
}
