package com.dorosoft.erp.catalog.application.api;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductMediaPublicUrlResolver;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 메뉴 Projection(GET /menu). 판매 비활성 상품과 그 결과 비어 있는 Category, 비활성 옵션,
 * 내부 version·stockManaged·Object Key·감사 정보를 응답에서 제외한다.
 */
@Service
class PublishedMenuReaderService implements PublishedMenuReader {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final ProductMediaPublicUrlResolver urlResolver;

    PublishedMenuReaderService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductMediaRepository productMediaRepository,
            CatalogRevisionRepository catalogRevisionRepository,
            ProductMediaPublicUrlResolver urlResolver) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productMediaRepository = productMediaRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.urlResolver = urlResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedMenu getPublishedMenu() {
        long revision =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .revision();

        List<PublishedCategory> categories = new ArrayList<>();
        for (Category category : categoryRepository.findAll()) {
            List<PublishedProduct> products =
                    productRepository.findByCategory(category.categoryId()).stream()
                            .filter(Product::salesEnabled)
                            .map(this::toPublishedProduct)
                            .toList();
            if (!products.isEmpty()) {
                categories.add(new PublishedCategory(category.categoryId(), category.name(), products));
            }
        }
        return new PublishedMenu(revision, categories);
    }

    private PublishedProduct toPublishedProduct(Product product) {
        List<PublishedOption> options =
                product.options().stream()
                        .filter(ProductOption::enabled)
                        .map(o -> new PublishedOption(o.optionId(), o.name(), o.additionalPrice()))
                        .toList();
        return new PublishedProduct(
                product.productId(),
                product.name(),
                product.description(),
                product.basePrice(),
                resolveImageUrl(product.mediaId()),
                product.imageAltText(),
                product.soldOut(),
                !product.soldOut(),
                options);
    }

    private String resolveImageUrl(UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        return productMediaRepository
                .findById(mediaId)
                .filter(ProductMedia::isReady)
                .map(media -> urlResolver.toPublicUrl(media.publishedObjectKey()))
                .orElse(null);
    }
}
