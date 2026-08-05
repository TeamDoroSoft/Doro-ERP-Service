package com.dorosoft.erp.catalog.application.query;

import java.util.List;

public record CatalogHistoryPage(List<CatalogHistoryEntry> items, String nextCursor) {}
