package com.dorosoft.erp.catalog.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProductCreationRequestHash - 생성 멱등성 비교용 요청 Hash(FR-MENU-001)")
class ProductCreationRequestHashTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();

    @Test
    @DisplayName("같은 입력값은 항상 같은 Hash를 만든다")
    void sameInputProducesSameHash() {
        String first = hash("아메리카노", 4500L);
        String second = hash("아메리카노", 4500L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("가격만 달라도 다른 Hash를 만든다")
    void differentPriceProducesDifferentHash() {
        assertThat(hash("아메리카노", 4500L)).isNotEqualTo(hash("아메리카노", 5000L));
    }

    @Test
    @DisplayName("이름만 달라도 다른 Hash를 만든다")
    void differentNameProducesDifferentHash() {
        assertThat(hash("아메리카노", 4500L)).isNotEqualTo(hash("카페라떼", 4500L));
    }

    private static String hash(String name, long basePrice) {
        return ProductCreationRequestHash.of(CATEGORY_ID, name, "설명", basePrice, MEDIA_ID, "대체텍스트", true, false);
    }
}
