package com.dorosoft.erp.commerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.commerce.domain.catalog.CatalogRuleException;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.CatalogViolation;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SOFT-435: Framework 없이 Domain 불변 규칙만 검증한다.
 */
class CatalogDomainTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void categoryNameIsTrimmed() {
        MenuCategory category = MenuCategory.create(UUID.randomUUID(), TENANT, "  커피  ", 1, CatalogStatus.ACTIVE);
        assertThat(category.name()).isEqualTo("커피");
    }

    @Test
    void blankCategoryNameIsRejected() {
        assertThatThrownBy(() -> MenuCategory.create(UUID.randomUUID(), TENANT, " ", 1, CatalogStatus.ACTIVE))
                .isInstanceOf(CatalogRuleException.class)
                .extracting(error -> ((CatalogRuleException) error).violation())
                .isEqualTo(CatalogViolation.NAME_REQUIRED);
    }

    @Test
    void categoryWithoutTenantIsRejected() {
        assertThatThrownBy(() -> MenuCategory.create(UUID.randomUUID(), null, "커피", 1, CatalogStatus.ACTIVE))
                .isInstanceOf(CatalogRuleException.class)
                .extracting(error -> ((CatalogRuleException) error).violation())
                .isEqualTo(CatalogViolation.TENANT_REQUIRED);
    }

    @Test
    void newProductIsNotSoldOut() {
        Product product = product(4500L);
        assertThat(product.soldOut()).isFalse();
        assertThat(product.version()).isZero();
    }

    @Test
    void zeroPriceIsAllowed() {
        assertThat(product(0L).price()).isZero();
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> product(-1L))
                .isInstanceOf(CatalogRuleException.class)
                .extracting(error -> ((CatalogRuleException) error).violation())
                .isEqualTo(CatalogViolation.PRICE_NEGATIVE);
    }

    @Test
    void negativePriceChangeIsRejected() {
        Product product = product(4500L);
        assertThatThrownBy(() -> product.changePrice(-1L)).isInstanceOf(CatalogRuleException.class);
        assertThat(product.price()).isEqualTo(4500L);
    }

    @Test
    void negativeDisplayOrderIsRejected() {
        assertThatThrownBy(() -> MenuCategory.create(UUID.randomUUID(), TENANT, "커피", -1, CatalogStatus.ACTIVE))
                .isInstanceOf(CatalogRuleException.class)
                .extracting(error -> ((CatalogRuleException) error).violation())
                .isEqualTo(CatalogViolation.DISPLAY_ORDER_NEGATIVE);
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertThat(product(4500L).describe("   ").description()).isNull();
    }

    @Test
    void productIsNotSellableWhenCategoryIsInactive() {
        MenuCategory inactive = MenuCategory.restore(
                UUID.randomUUID(), TENANT, "커피", 1, CatalogStatus.INACTIVE, 0L);
        assertThat(product(4500L).isSellableUnder(inactive)).isFalse();
    }

    @Test
    void productIsNotSellableWhenSoldOut() {
        MenuCategory active = MenuCategory.restore(UUID.randomUUID(), TENANT, "커피", 1, CatalogStatus.ACTIVE, 0L);
        assertThat(product(4500L).changeSoldOut(true).isSellableUnder(active)).isFalse();
    }

    @Test
    void productIsNotSellableWhenInactive() {
        MenuCategory active = MenuCategory.restore(UUID.randomUUID(), TENANT, "커피", 1, CatalogStatus.ACTIVE, 0L);
        assertThat(product(4500L).changeStatus(CatalogStatus.INACTIVE).isSellableUnder(active)).isFalse();
    }

    @Test
    void statusChangeKeepsIdentityAndVersion() {
        Product product = Product.restore(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), "아메리카노", null, 4500L, false,
                CatalogStatus.ACTIVE, 1, 7L);

        Product deactivated = product.changeStatus(CatalogStatus.INACTIVE);

        assertThat(deactivated.id()).isEqualTo(product.id());
        assertThat(deactivated.version()).isEqualTo(7L);
        assertThat(deactivated.isActive()).isFalse();
    }

    private static Product product(long price) {
        return Product.create(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), "아메리카노", null, price, CatalogStatus.ACTIVE, 1);
    }
}
