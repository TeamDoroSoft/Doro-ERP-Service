package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리 화면 전체 Catalog 조회(GET /catalog). 비활성 Category·Product·Option도 모두 포함한다. */
@Service
public class CatalogOverviewQueryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    public CatalogOverviewQueryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CatalogRevisionRepository catalogRevisionRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional(readOnly = true)
    public CatalogOverview getOverview() {
        long revision =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .revision();

        List<CategoryOverview> categories =
                categoryRepository.findAll().stream()
                        .map(category -> new CategoryOverview(category, productRepository.findByCategory(category.categoryId())))
                        .toList();

        return new CatalogOverview(revision, categories);
    }
}
