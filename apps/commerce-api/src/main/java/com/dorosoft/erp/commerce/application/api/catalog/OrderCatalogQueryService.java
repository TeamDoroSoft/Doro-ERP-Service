package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderLineQuote;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderLineRequest;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderQuote;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.ProductSnapshot;
import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.port.catalog.MenuCategoryRepositoryPort;
import com.dorosoft.erp.commerce.application.port.catalog.ProductRepositoryPort;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문용 Catalog 조회 구현 (SOFT-437).
 *
 * <p>Tenant Scope와 판매 가능 여부를 Repository Query가 아니라 Domain 규칙
 * ({@link Product#isSellableUnder(MenuCategory)})으로 판단해 판매 메뉴 조회와 같은 기준을 쓴다.
 */
@Service
public class OrderCatalogQueryService implements OrderCatalogQueryUseCase {

    static final int MAX_ORDER_LINES = 100;
    static final int MAX_QUANTITY_PER_LINE = 999;

    private final MenuCategoryRepositoryPort categoryRepository;
    private final ProductRepositoryPort productRepository;

    public OrderCatalogQueryService(
            MenuCategoryRepositoryPort categoryRepository, ProductRepositoryPort productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderQuote quoteOrderLines(List<OrderLineRequest> lines) {
        ActorContext actor = requireActor();
        requireLines(lines);

        Set<UUID> productIds = new LinkedHashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            OrderLineRequest line = lines.get(index);
            if (line == null || line.productId() == null) {
                throw validationFailed("lines[" + index + "].productId", "REQUIRED", "상품을 선택하세요.");
            }
            if (line.quantity() < 1) {
                throw validationFailed("lines[" + index + "].quantity", "QUANTITY_TOO_SMALL",
                        "수량은 1개 이상이어야 합니다.");
            }
            if (line.quantity() > MAX_QUANTITY_PER_LINE) {
                throw validationFailed("lines[" + index + "].quantity", "QUANTITY_TOO_LARGE",
                        "수량이 허용 범위를 넘었습니다.");
            }
            productIds.add(line.productId());
        }

        // Tenant Scope는 Query 조건으로 적용한다. 다른 Tenant 상품은 애초에 조회되지 않는다.
        Map<UUID, Product> productsById = productRepository
                .findAllByTenantAndIds(actor.tenantId(), productIds).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));

        Set<UUID> categoryIds = productsById.values().stream()
                .map(Product::categoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, MenuCategory> categoriesById = categoryRepository
                .findAllByTenantAndIds(actor.tenantId(), categoryIds).stream()
                .collect(Collectors.toMap(MenuCategory::id, Function.identity()));

        List<OrderLineQuote> quotes = new ArrayList<>(lines.size());
        long totalAmount = 0L;

        for (int index = 0; index < lines.size(); index++) {
            OrderLineRequest line = lines.get(index);
            Product product = productsById.get(line.productId());
            if (product == null) {
                // 다른 Tenant 상품도 여기로 온다. 존재 여부를 구분해 노출하지 않는다.
                throw new ProblemAwareException(
                        CatalogErrorCode.PRODUCT_NOT_FOUND,
                        "주문할 수 없는 상품이 포함되어 있습니다.",
                        List.of(new ProblemFieldError("lines[" + index + "].productId", "PRODUCT_NOT_FOUND")));
            }

            MenuCategory category = categoriesById.get(product.categoryId());
            if (!product.isSellableUnder(category)) {
                // 메뉴 조회 이후 품절·비활성으로 바뀐 경우가 여기서 걸린다.
                throw new ProblemAwareException(
                        CatalogErrorCode.PRODUCT_NOT_SELLABLE,
                        "현재 판매할 수 없는 상품이 포함되어 있습니다. 메뉴를 새로고침하세요.",
                        List.of(new ProblemFieldError(
                                "lines[" + index + "].productId", "PRODUCT_NOT_SELLABLE")));
            }

            // 단가는 오직 서버가 조회한 Catalog 가격이다.
            long unitPrice = product.price();
            long lineAmount = unitPrice * line.quantity();
            totalAmount += lineAmount;

            quotes.add(new OrderLineQuote(
                    new ProductSnapshot(product.id(), product.name(), unitPrice),
                    line.quantity(),
                    lineAmount));
        }

        return new OrderQuote(CatalogService.CURRENCY_KRW, quotes, totalAmount);
    }

    private void requireLines(List<OrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw validationFailed("lines", "REQUIRED", "주문할 상품을 하나 이상 선택하세요.");
        }
        if (lines.size() > MAX_ORDER_LINES) {
            throw validationFailed("lines", "TOO_MANY_LINES", "주문 항목이 너무 많습니다.");
        }
    }

    private ActorContext requireActor() {
        return ActorContextHolder.current().orElseThrow(() -> new ProblemAwareException(
                CatalogErrorCode.AUTHENTICATION_REQUIRED, "인증이 필요합니다."));
    }

    private static ProblemAwareException validationFailed(String field, String code, String detail) {
        return new ProblemAwareException(
                CatalogErrorCode.VALIDATION_FAILED, detail, List.of(new ProblemFieldError(field, code)));
    }
}
