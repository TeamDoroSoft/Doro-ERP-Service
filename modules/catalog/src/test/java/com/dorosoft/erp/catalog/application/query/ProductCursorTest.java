package com.dorosoft.erp.catalog.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.domain.query.InvalidCursorException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProductCursor 인코딩·디코딩")
class ProductCursorTest {

    @Test
    @DisplayName("encode한 값을 decode하면 원래 productId를 돌려준다")
    void roundTrips() {
        UUID productId = UUID.randomUUID();

        String encoded = ProductCursor.encode(productId);

        assertThat(ProductCursor.decode(encoded)).isEqualTo(productId);
    }

    @Test
    @DisplayName("형식이 아닌 문자열은 InvalidCursorException")
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> ProductCursor.decode("not-a-valid-cursor!!!")).isInstanceOf(InvalidCursorException.class);
    }
}
