package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.product.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.product.MediaNotReadyException;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductCreationRequestHash;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 생성(FR-MENU-001). Idempotency-Key 재요청은 같은 내용이면 기존 결과를, 다른 내용이면 거부를 반환한다. */
@Service
public class CreateProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    public CreateProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMediaRepository productMediaRepository,
            CatalogRevisionRepository catalogRevisionRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional
    public Product create(CreateProductCommand command) {
        String requestHash =
                ProductCreationRequestHash.of(
                        command.categoryId(),
                        command.name(),
                        command.description(),
                        command.basePrice(),
                        command.mediaId(),
                        command.imageAltText(),
                        command.salesEnabled(),
                        command.stockManaged());

        if (command.idempotencyKey() != null) {
            Product existing = productRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
            if (existing != null) {
                if (!requestHash.equals(existing.idempotencyRequestHash())) {
                    throw new IdempotencyKeyReusedException(command.idempotencyKey());
                }
                return existing;
            }
        }

        if (categoryRepository.findById(command.categoryId()).isEmpty()) {
            throw new CategoryNotFoundException(command.categoryId());
        }
        validateMediaReady(command.mediaId());

        UUID catalogId =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .catalogId();
        int nextDisplayOrder = (int) productRepository.countByCategory(command.categoryId());

        Product product =
                Product.create(
                        UUID.randomUUID(),
                        catalogId,
                        command.categoryId(),
                        command.mediaId(),
                        command.name(),
                        command.description(),
                        command.basePrice(),
                        command.imageAltText(),
                        command.salesEnabled(),
                        command.stockManaged(),
                        nextDisplayOrder,
                        Instant.now(),
                        command.idempotencyKey(),
                        requestHash);
        return productRepository.save(product);
    }

    private void validateMediaReady(UUID mediaId) {
        if (mediaId == null) {
            return;
        }
        ProductMedia media = productMediaRepository.findById(mediaId).orElseThrow(() -> new MediaNotFoundException(mediaId));
        if (!media.isReady()) {
            throw new MediaNotReadyException(mediaId);
        }
    }
}
