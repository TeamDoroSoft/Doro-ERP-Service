package com.dorosoft.erp.catalog.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.domain.orderability.OrderabilityReason;
import com.dorosoft.erp.catalog.domain.orderability.OrderabilityRejectedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Product Aggregate 불변식(FR-MENU-001, 002, 004)")
class ProductTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private static Product product() {
        return Product.create(
                PRODUCT_ID, CATALOG_ID, CATEGORY_ID, null, "아메리카노", "진한 에스프레소와 물", 4500L, null,
                true, false, 0, Instant.now(), null, null);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("soldOut=false, version=0으로 생성한다")
        void createsWithSoldOutFalseAndVersionZero() {
            Product product = product();

            assertThat(product.soldOut()).isFalse();
            assertThat(product.version()).isZero();
            assertThat(product.options()).isEmpty();
        }

        @Test
        @DisplayName("가격이 음수면 InvalidPriceException")
        void rejectsNegativePrice() {
            assertThatThrownBy(
                            () ->
                                    Product.create(
                                            PRODUCT_ID, CATALOG_ID, CATEGORY_ID, null, "이름", null, -1L, null,
                                            true, false, 0, Instant.now(), null, null))
                    .isInstanceOf(InvalidPriceException.class);
        }

        @Test
        @DisplayName("이름이 공백뿐이면 IllegalArgumentException")
        void rejectsBlankName() {
            assertThatThrownBy(
                            () ->
                                    Product.create(
                                            PRODUCT_ID, CATALOG_ID, CATEGORY_ID, null, "   ", null, 1000L, null,
                                            true, false, 0, Instant.now(), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("설명이 2000자를 초과하면 IllegalArgumentException")
        void rejectsDescriptionOver2000() {
            String tooLong = "가".repeat(2001);

            assertThatThrownBy(
                            () ->
                                    Product.create(
                                            PRODUCT_ID, CATALOG_ID, CATEGORY_ID, null, "이름", tooLong, 1000L, null,
                                            true, false, 0, Instant.now(), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("설명이 null이면 허용한다")
        void allowsNullDescription() {
            assertThat(product().description()).isEqualTo("진한 에스프레소와 물");

            Product withoutDescription =
                    Product.create(
                            PRODUCT_ID, CATALOG_ID, CATEGORY_ID, null, "이름", null, 1000L, null,
                            true, false, 0, Instant.now(), null, null);
            assertThat(withoutDescription.description()).isNull();
        }
    }

    @Nested
    @DisplayName("replaceBasicInfo")
    class ReplaceBasicInfo {

        @Test
        @DisplayName("기본 정보만 바꾸고 soldOut·options·version은 그대로다")
        void replacesBasicInfoOnly() {
            Product original = product();
            UUID newCategoryId = UUID.randomUUID();
            UUID newMediaId = UUID.randomUUID();

            Product updated =
                    original.replaceBasicInfo(
                            newCategoryId, newMediaId, "카페라떼", "부드러운 우유 거품", 5000L, "카페라떼 한 잔", false, true, 3);

            assertThat(updated.categoryId()).isEqualTo(newCategoryId);
            assertThat(updated.mediaId()).isEqualTo(newMediaId);
            assertThat(updated.name()).isEqualTo("카페라떼");
            assertThat(updated.basePrice().amount()).isEqualTo(5000L);
            assertThat(updated.salesEnabled()).isFalse();
            assertThat(updated.stockManaged()).isTrue();
            assertThat(updated.displayOrder()).isEqualTo(3);
            assertThat(updated.soldOut()).isEqualTo(original.soldOut());
            assertThat(updated.version()).isEqualTo(original.version());
        }
    }

    @Nested
    @DisplayName("changeSalesPolicy")
    class ChangeSalesPolicy {

        @Test
        @DisplayName("salesEnabled·stockManaged만 바꾸고 나머지 필드는 그대로다")
        void changesOnlyPolicyFields() {
            Product original = product();

            Product updated = original.changeSalesPolicy(false, true);

            assertThat(updated.salesEnabled()).isFalse();
            assertThat(updated.stockManaged()).isTrue();
            assertThat(updated.name()).isEqualTo(original.name());
            assertThat(updated.soldOut()).isEqualTo(original.soldOut());
            assertThat(updated.version()).isEqualTo(original.version());
        }
    }

    @Nested
    @DisplayName("changeSoldOut")
    class ChangeSoldOut {

        @Test
        @DisplayName("soldOut만 바꾸고 나머지 필드는 그대로다")
        void changesOnlySoldOut() {
            Product original = product();

            Product soldOut = original.changeSoldOut(true);

            assertThat(soldOut.soldOut()).isTrue();
            assertThat(soldOut.salesEnabled()).isEqualTo(original.salesEnabled());
            assertThat(soldOut.stockManaged()).isEqualTo(original.stockManaged());
            assertThat(soldOut.version()).isEqualTo(original.version());
        }
    }

    @Nested
    @DisplayName("replaceOptions")
    class ReplaceOptions {

        @Test
        @DisplayName("새 옵션은 optionId를 서버가 부여하고 배열 순서대로 displayOrder를 매긴다")
        void assignsIdsAndOrderForNewOptions() {
            Product withOptions =
                    product()
                            .replaceOptions(
                                    List.of(
                                            new ProductOptionRequest(null, "샷 추가", 500L, true),
                                            new ProductOptionRequest(null, "디카페인 변경", 700L, true)));

            assertThat(withOptions.options()).hasSize(2);
            assertThat(withOptions.options().get(0).optionId()).isNotNull();
            assertThat(withOptions.options().get(0).displayOrder()).isZero();
            assertThat(withOptions.options().get(1).displayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("기존 옵션 ID를 누락하면 OptionOmissionNotAllowedException")
        void rejectsOmittedExistingOption() {
            Product withOptions =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)));
            UUID existingId = withOptions.options().get(0).optionId();

            assertThatThrownBy(() -> withOptions.replaceOptions(List.of(new ProductOptionRequest(null, "새 옵션", 100L, true))))
                    .isInstanceOf(OptionOmissionNotAllowedException.class);

            // 누락 대신 enabled=false로 비활성화하는 것은 허용된다.
            Product disabled =
                    withOptions.replaceOptions(List.of(new ProductOptionRequest(existingId, "샷 추가", 500L, false)));
            assertThat(disabled.options().get(0).enabled()).isFalse();
        }

        @Test
        @DisplayName("다른 상품의 옵션이거나 존재하지 않는 옵션 ID는 InvalidProductOptionsException")
        void rejectsForeignOrUnknownOptionId() {
            Product withOptions =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)));
            UUID foreignOptionId = UUID.randomUUID();

            assertThatThrownBy(
                            () ->
                                    withOptions.replaceOptions(
                                            List.of(new ProductOptionRequest(foreignOptionId, "다른 상품 옵션", 100L, true))))
                    .isInstanceOf(InvalidProductOptionsException.class);
        }

        @Test
        @DisplayName("요청 안에서 같은 옵션 ID가 중복되면 InvalidProductOptionsException")
        void rejectsDuplicateOptionIdInRequest() {
            Product withOptions =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)));
            UUID existingId = withOptions.options().get(0).optionId();

            assertThatThrownBy(
                            () ->
                                    withOptions.replaceOptions(
                                            List.of(
                                                    new ProductOptionRequest(existingId, "샷 추가", 500L, true),
                                                    new ProductOptionRequest(existingId, "샷 추가2", 600L, true))))
                    .isInstanceOf(InvalidProductOptionsException.class);
        }

        @Test
        @DisplayName("옵션 추가 금액이 음수면 InvalidPriceException")
        void rejectsNegativeOptionPrice() {
            assertThatThrownBy(
                            () -> product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", -500L, true))))
                    .isInstanceOf(InvalidPriceException.class);
        }
    }

    @Nested
    @DisplayName("resolveOrderableOptions(판매 가능성 판정 명세)")
    class ResolveOrderableOptions {

        @Test
        @DisplayName("옵션을 선택하지 않아도 통과한다")
        void allowsNoOptionSelection() {
            List<ProductOption> resolved =
                    product().resolveOrderableOptions(List.of());

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("null 옵션 목록도 빈 선택으로 취급한다")
        void treatsNullAsEmptySelection() {
            assertThat(product().resolveOrderableOptions(null)).isEmpty();
        }

        @Test
        @DisplayName("판매 비활성 상품은 OrderabilityRejectedException(PRODUCT_NOT_FOR_SALE)")
        void rejectsWhenNotForSale() {
            Product notForSale = product().changeSalesPolicy(false, false);

            assertThatThrownBy(() -> notForSale.resolveOrderableOptions(List.of()))
                    .isInstanceOf(OrderabilityRejectedException.class)
                    .extracting(e -> ((OrderabilityRejectedException) e).reason())
                    .isEqualTo(OrderabilityReason.PRODUCT_NOT_FOR_SALE);
        }

        @Test
        @DisplayName("수동 품절 상품은 OrderabilityRejectedException(PRODUCT_SOLD_OUT)")
        void rejectsWhenSoldOut() {
            Product soldOut = product().changeSoldOut(true);

            assertThatThrownBy(() -> soldOut.resolveOrderableOptions(List.of()))
                    .isInstanceOf(OrderabilityRejectedException.class)
                    .extracting(e -> ((OrderabilityRejectedException) e).reason())
                    .isEqualTo(OrderabilityReason.PRODUCT_SOLD_OUT);
        }

        @Test
        @DisplayName("같은 옵션을 중복 선택하면 OrderabilityRejectedException(DUPLICATE_OPTION_SELECTION)")
        void rejectsDuplicateSelection() {
            Product withOption =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)));
            UUID optionId = withOption.options().get(0).optionId();

            assertThatThrownBy(() -> withOption.resolveOrderableOptions(List.of(optionId, optionId)))
                    .isInstanceOf(OrderabilityRejectedException.class)
                    .extracting(e -> ((OrderabilityRejectedException) e).reason())
                    .isEqualTo(OrderabilityReason.DUPLICATE_OPTION_SELECTION);
        }

        @Test
        @DisplayName("존재하지 않는(또는 다른 상품의) 옵션 ID는 OrderabilityRejectedException(OPTION_NOT_FOUND)")
        void rejectsUnknownOption() {
            Product withOption =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)));

            assertThatThrownBy(() -> withOption.resolveOrderableOptions(List.of(UUID.randomUUID())))
                    .isInstanceOf(OrderabilityRejectedException.class)
                    .extracting(e -> ((OrderabilityRejectedException) e).reason())
                    .isEqualTo(OrderabilityReason.OPTION_NOT_FOUND);
        }

        @Test
        @DisplayName("비활성 옵션을 선택하면 OrderabilityRejectedException(OPTION_NOT_ORDERABLE)")
        void rejectsDisabledOption() {
            Product withDisabledOption =
                    product().replaceOptions(List.of(new ProductOptionRequest(null, "단종 옵션", 500L, false)));
            UUID optionId = withDisabledOption.options().get(0).optionId();

            assertThatThrownBy(() -> withDisabledOption.resolveOrderableOptions(List.of(optionId)))
                    .isInstanceOf(OrderabilityRejectedException.class)
                    .extracting(e -> ((OrderabilityRejectedException) e).reason())
                    .isEqualTo(OrderabilityReason.OPTION_NOT_ORDERABLE);
        }

        @Test
        @DisplayName("모두 통과하면 선택한 옵션을 요청 순서대로 반환한다")
        void resolvesSelectedOptionsInRequestOrder() {
            Product withOptions =
                    product()
                            .replaceOptions(
                                    List.of(
                                            new ProductOptionRequest(null, "샷 추가", 500L, true),
                                            new ProductOptionRequest(null, "디카페인 변경", 700L, true)));
            UUID shot = withOptions.options().get(0).optionId();
            UUID decaf = withOptions.options().get(1).optionId();

            List<ProductOption> resolved =
                    withOptions.resolveOrderableOptions(List.of(decaf, shot));

            assertThat(resolved).extracting(ProductOption::optionId)
                    .containsExactly(decaf, shot);
        }
    }
}
