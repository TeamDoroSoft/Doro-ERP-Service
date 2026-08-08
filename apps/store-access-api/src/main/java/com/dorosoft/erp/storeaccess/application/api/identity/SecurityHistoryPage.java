package com.dorosoft.erp.storeaccess.application.api.identity;

import java.util.List;

/**
 * A Controller-facing page of {@link SecurityHistoryEntry} (ADR-02-015). {@code hasMore} is determined by
 * fetching one record past {@code size} rather than a separate {@code count} query.
 */
public record SecurityHistoryPage(List<SecurityHistoryEntry> items, boolean hasMore) {
}
