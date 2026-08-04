package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.product.Product;
import java.util.List;

/** 관리 화면용 Category와 그 안의 모든 Product(활성 여부 무관). */
public record CategoryOverview(Category category, List<Product> products) {}
