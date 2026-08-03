package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.domain.product.Product;
import java.util.List;

public record ProductListPage(List<Product> items, String nextCursor, boolean hasMore) {}
