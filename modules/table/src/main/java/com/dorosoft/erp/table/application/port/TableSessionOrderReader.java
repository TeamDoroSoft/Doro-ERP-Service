package com.dorosoft.erp.table.application.port;

import com.dorosoft.erp.table.application.dto.TableOrderSummaryResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface TableSessionOrderReader {

    OrderPage findOrders(OrderQuery query);

    boolean supportsStatus(String status);

    record OrderQuery(
            UUID tableId,
            UUID sessionId,
            String status,
            String cursor,
            int size) {
        public OrderQuery {
            Objects.requireNonNull(tableId, "tableId");
            Objects.requireNonNull(sessionId, "sessionId");
        }
    }

    record OrderPage(List<TableOrderSummaryResponse> items, String nextCursor) {
        public OrderPage {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static OrderPage empty() {
            return new OrderPage(List.of(), null);
        }
    }
}
