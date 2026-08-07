package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import java.util.UUID;

public interface CatalogCommandUseCase {

    CategoryView createCategory(CreateCategoryCommand command);

    CategoryView updateCategory(UUID categoryId, Long expectedVersion, UpdateCategoryCommand command);

    ProductView createProduct(CreateProductCommand command);

    ProductView updateProduct(UUID productId, Long expectedVersion, UpdateProductCommand command);

    ProductView changeSoldOut(UUID productId, Long expectedVersion, ChangeSoldOutCommand command);
}
