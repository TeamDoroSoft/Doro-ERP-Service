package com.dorosoft.erp.order.application.calculation;

import com.dorosoft.erp.catalog.application.api.OrderCatalogReader;
import com.dorosoft.erp.catalog.application.api.OrderCatalogSnapshot;
import com.dorosoft.erp.catalog.application.api.SelectedOptionSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 테스트 전용 In-memory OrderCatalogReader. 미리 등록한 Snapshot을 그대로 반환한다. */
class FakeOrderCatalogReader implements OrderCatalogReader {

    private final Map<UUID, OrderCatalogSnapshot> snapshotsByProductId = new HashMap<>();
    private final Map<UUID, SelectedOptionSnapshot> optionsByOptionId = new HashMap<>();

    void register(OrderCatalogSnapshot snapshot) {
        snapshotsByProductId.put(snapshot.productId(), snapshot);
        for (SelectedOptionSnapshot option : snapshot.selectedOptions()) {
            optionsByOptionId.put(option.optionId(), option);
        }
    }

    @Override
    public OrderCatalogSnapshot resolveForOrder(UUID productId, List<UUID> selectedOptionIds) {
        OrderCatalogSnapshot base = snapshotsByProductId.get(productId);
        if (base == null) {
            throw new IllegalStateException("등록되지 않은 productId=" + productId);
        }
        List<SelectedOptionSnapshot> selected = selectedOptionIds.stream().map(optionsByOptionId::get).toList();
        return new OrderCatalogSnapshot(
                base.productId(), base.productName(), base.baseUnitPrice(), selected, base.stockManaged(), base.catalogRevision());
    }
}
