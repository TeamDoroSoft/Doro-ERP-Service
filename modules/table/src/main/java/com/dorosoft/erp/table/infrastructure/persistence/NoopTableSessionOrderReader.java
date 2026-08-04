package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableSessionOrderReader;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class NoopTableSessionOrderReader implements TableSessionOrderReader {

    private static final Set<String> SUPPORTED_STATUSES = Set.of("COMPLETED", "IN_PROGRESS", "CANCELLED");

    @Override
    public OrderPage findOrders(OrderQuery query) {
        return OrderPage.empty();
    }

    @Override
    public boolean supportsStatus(String status) {
        return SUPPORTED_STATUSES.contains(status);
    }
}
