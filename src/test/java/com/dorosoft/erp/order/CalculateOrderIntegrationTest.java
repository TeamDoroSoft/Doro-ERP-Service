package com.dorosoft.erp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import com.dorosoft.erp.order.application.calculation.CalculateOrderCommand;
import com.dorosoft.erp.order.application.calculation.CalculateOrderService;
import com.dorosoft.erp.order.application.calculation.OrderItemSelection;
import com.dorosoft.erp.order.application.port.OrderRepository;
import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import com.dorosoft.erp.order.domain.order.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** OrderCatalogReader 조회부터 orders·order_item·order_item_option Snapshot 저장까지 실제 MySQL로 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("CalculateOrderService 통합 테스트 - Catalog Snapshot 조회부터 Order Snapshot 저장까지")
class CalculateOrderIntegrationTest {

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private CreateProductService createProductService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private CalculateOrderService calculateOrderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private TransactionTemplate transactionTemplate;

    private AuditContext auditContext;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM order_item_option").update();
        jdbcClient.sql("DELETE FROM order_item").update();
        jdbcClient.sql("DELETE FROM orders").update();
        jdbcClient.sql("DELETE FROM product_option").update();
        jdbcClient.sql("DELETE FROM product").update();
        jdbcClient.sql("DELETE FROM category").update();

        auditContext = new AuditContext("test-actor", "ADMIN", Instant.now(), UUID.randomUUID().toString());
        Category category = createCategoryService.create("커피", null, auditContext);
        categoryId = category.categoryId();
    }

    private Product createProduct(String name, long basePrice) {
        return createProductService.create(
                new CreateProductCommand(categoryId, name, null, basePrice, null, null, true, false, null), auditContext);
    }

    @Test
    @DisplayName("옵션 포함 여러 항목의 금액을 계산해 Snapshot을 저장하고 다시 읽으면 그대로 남아있다")
    void calculatesAndPersistsOrderSnapshot() {
        Product americano = createProduct("아메리카노", 4500L);
        Product withOption =
                replaceProductOptionsService.replaceOptions(
                        americano.productId(),
                        List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)),
                        americano.version(),
                        auditContext);
        UUID optionId = withOption.options().get(0).optionId();
        Product latte = createProduct("카페라떼", 5000L);

        CalculateOrderCommand command =
                new CalculateOrderCommand(
                        List.of(
                                new OrderItemSelection(americano.productId(), List.of(optionId), 2),
                                new OrderItemSelection(latte.productId(), List.of(), 1)));

        Order saved = calculateOrderService.calculate(command);

        assertThat(saved.totalAmount().amount()).isEqualTo(15000L); // (4500+500)*2 + 5000*1
        assertThat(saved.items()).hasSize(2);

        Order reloaded =
                transactionTemplate.execute(status -> orderRepository.findById(saved.orderId()).orElseThrow());
        assertThat(reloaded.totalAmount().amount()).isEqualTo(15000L);
        assertThat(reloaded.items()).hasSize(2);

        var americanoLine =
                reloaded.items().stream().filter(i -> i.productId().equals(americano.productId())).findFirst().orElseThrow();
        assertThat(americanoLine.productName()).isEqualTo("아메리카노");
        assertThat(americanoLine.baseUnitPrice()).isEqualTo(4500L);
        assertThat(americanoLine.quantity()).isEqualTo(2);
        assertThat(americanoLine.price().unitPrice().amount()).isEqualTo(5000L);
        assertThat(americanoLine.price().lineTotal().amount()).isEqualTo(10000L);
        assertThat(americanoLine.options()).hasSize(1);
        assertThat(americanoLine.options().get(0).optionName()).isEqualTo("샷 추가");
        assertThat(americanoLine.options().get(0).additionalPrice()).isEqualTo(500L);
    }

    @Test
    @DisplayName("주문 항목이 없으면 EMPTY_ORDER이고 저장하지 않는다")
    void rejectsEmptyOrder() {
        assertThatThrownBy(() -> calculateOrderService.calculate(new CalculateOrderCommand(List.of())))
                .isInstanceOf(EmptyOrderException.class);

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM orders").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("quantity=0은 INVALID_QUANTITY이고 저장하지 않는다")
    void rejectsZeroQuantity() {
        Product americano = createProduct("아메리카노", 4500L);
        CalculateOrderCommand command =
                new CalculateOrderCommand(List.of(new OrderItemSelection(americano.productId(), List.of(), 0)));

        assertThatThrownBy(() -> calculateOrderService.calculate(command)).isInstanceOf(InvalidQuantityException.class);

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM orders").query(Long.class).single()).isZero();
    }
}
