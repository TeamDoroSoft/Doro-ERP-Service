package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.product.MediaNotReadyException;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 기본 정보 전체 교체(FR-MENU-002). soldOut과 options는 이 Use Case가 다루지 않는다. */
@Service
public class UpdateProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;

    public UpdateProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMediaRepository productMediaRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
    }

    @Transactional
    public Product replaceBasicInfo(UUID productId, ReplaceProductBasicInfoCommand command, long expectedVersion) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }

        if (categoryRepository.findById(command.categoryId()).isEmpty()) {
            throw new CategoryNotFoundException(command.categoryId());
        }
        validateMediaReady(command.mediaId());

        int displayOrder =
                current.categoryId().equals(command.categoryId())
                        ? current.displayOrder()
                        : (int) productRepository.countByCategory(command.categoryId());

        Product updated =
                current.replaceBasicInfo(
                        command.categoryId(),
                        command.mediaId(),
                        command.name(),
                        command.description(),
                        command.basePrice(),
                        command.imageAltText(),
                        command.salesEnabled(),
                        command.stockManaged(),
                        displayOrder);
        return productRepository.save(updated);
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
