package com.dorosoft.erp.catalog.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Category 불변식(FR-MENU-003)")
class CategoryTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID CATALOG_ID = UUID.randomUUID();

    @Test
    @DisplayName("create는 version=0으로 생성한다")
    void createStartsAtVersionZero() {
        Category category = Category.create(CATEGORY_ID, CATALOG_ID, "커피", 0, Instant.now());

        assertThat(category.version()).isZero();
        assertThat(category.displayOrder()).isZero();
        assertThat(category.name()).isEqualTo("커피");
    }

    @Test
    @DisplayName("name은 앞뒤 공백을 제거해 저장한다")
    void trimsName() {
        Category category = Category.create(CATEGORY_ID, CATALOG_ID, "  커피  ", 0, Instant.now());

        assertThat(category.name()).isEqualTo("커피");
    }

    @Test
    @DisplayName("공백 제거 후 빈 문자열이면 IllegalArgumentException")
    void rejectsBlankName() {
        assertThatThrownBy(() -> Category.create(CATEGORY_ID, CATALOG_ID, "   ", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("60자를 초과하면 IllegalArgumentException")
    void rejectsNameOver60Chars() {
        String tooLong = "가".repeat(61);

        assertThatThrownBy(() -> Category.create(CATEGORY_ID, CATALOG_ID, tooLong, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("60자는 허용한다")
    void allowsExactly60Chars() {
        String exactly60 = "가".repeat(60);

        Category category = Category.create(CATEGORY_ID, CATALOG_ID, exactly60, 0, Instant.now());

        assertThat(category.name()).hasSize(60);
    }

    @Test
    @DisplayName("displayOrder가 음수면 IllegalArgumentException")
    void rejectsNegativeDisplayOrder() {
        assertThatThrownBy(() -> Category.create(CATEGORY_ID, CATALOG_ID, "커피", -1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rename은 name만 바꾸고 displayOrder·version은 유지한다")
    void renameKeepsOtherFields() {
        Category category = Category.create(CATEGORY_ID, CATALOG_ID, "커피", 3, Instant.now());

        Category renamed = category.rename("커피·에스프레소");

        assertThat(renamed.name()).isEqualTo("커피·에스프레소");
        assertThat(renamed.displayOrder()).isEqualTo(3);
        assertThat(renamed.version()).isEqualTo(category.version());
        assertThat(renamed.categoryId()).isEqualTo(category.categoryId());
    }
}
