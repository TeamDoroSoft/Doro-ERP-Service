package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.application.port.ProductOrderRepository;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Product 정렬(FR-MENU-008) 전용 좁은 Adapter. Product 전체 Aggregate는 MENU-04가 소유하며
 * 아직 JPA Entity가 없으므로, 이 Adapter는 product_id·category_id·display_order 세 컬럼만
 * 네이티브 SQL로 다뤄 MENU-04의 Product Entity와 매핑이 겹치지 않게 한다.
 */
@Repository
class JdbcProductOrderRepositoryAdapter implements ProductOrderRepository {

    private final JdbcClient jdbcClient;

    JdbcProductOrderRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<UUID> findProductIdsByCategory(UUID categoryId) {
        return jdbcClient
                .sql("SELECT product_id FROM product WHERE category_id = ? ORDER BY display_order ASC")
                .param(toBinary(categoryId))
                .query((rs, rowNum) -> toUuid(rs.getBytes(1)))
                .list();
    }

    /** display_order >= 0 CHECK 제약을 지키면서 UNIQUE 충돌도 피하는 임시 오프셋. */
    private static final int REORDER_TEMP_OFFSET = 1_000_000;

    @Override
    public void replaceDisplayOrder(UUID categoryId, List<UUID> orderedProductIds) {
        // 1단계: 큰 양수 오프셋으로 임시 이동해 (category_id, display_order) UNIQUE 제약 충돌을 피한다.
        // display_order >= 0 CHECK 제약이 있어 음수는 사용할 수 없다.
        for (int i = 0; i < orderedProductIds.size(); i++) {
            updateDisplayOrder(orderedProductIds.get(i), REORDER_TEMP_OFFSET + i);
        }
        // 2단계: 0부터 최종 값을 부여한다.
        for (int i = 0; i < orderedProductIds.size(); i++) {
            updateDisplayOrder(orderedProductIds.get(i), i);
        }
    }

    private void updateDisplayOrder(UUID productId, int displayOrder) {
        jdbcClient
                .sql("UPDATE product SET display_order = ? WHERE product_id = ?")
                .param(displayOrder)
                .param(toBinary(productId))
                .update();
    }

    private static byte[] toBinary(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    private static UUID toUuid(byte[] binary) {
        ByteBuffer buffer = ByteBuffer.wrap(binary);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
