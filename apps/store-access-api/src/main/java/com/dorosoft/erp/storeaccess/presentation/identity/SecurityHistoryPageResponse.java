package com.dorosoft.erp.storeaccess.presentation.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecurityHistoryPageResponse(
        List<SecurityHistoryEntryResponse> items, Instant nextCursorOccurredAt, UUID nextCursorId, boolean hasMore) {
}
