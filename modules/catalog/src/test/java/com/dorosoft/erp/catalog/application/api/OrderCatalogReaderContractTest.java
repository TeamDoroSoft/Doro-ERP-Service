package com.dorosoft.erp.catalog.application.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderCatalogReader 공개 계약 구조 제약(모듈 경계) - Order가 Catalog 내부 타입을 보지 않는지 확인한다")
class OrderCatalogReaderContractTest {

    @Test
    @DisplayName("resolveForOrder는 productId·선택 옵션 ID만 받고 OrderCatalogSnapshot만 반환한다")
    void methodSignatureExposesOnlyPublicTypes() throws NoSuchMethodException {
        Method method = OrderCatalogReader.class.getDeclaredMethod("resolveForOrder", UUID.class, java.util.List.class);

        assertThat(method.getReturnType()).isEqualTo(OrderCatalogSnapshot.class);
    }

    @Test
    @DisplayName("OrderCatalogSnapshot·SelectedOptionSnapshot은 Catalog 내부 Package(domain·infrastructure) 타입을 필드로 갖지 않는다")
    void snapshotDtosDoNotLeakInternalTypes() {
        assertNoInternalFieldTypes(OrderCatalogSnapshot.class);
        assertNoInternalFieldTypes(SelectedOptionSnapshot.class);
    }

    private static void assertNoInternalFieldTypes(Class<?> dtoType) {
        for (Field field : dtoType.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            String packageName = fieldType.getPackageName();
            assertThat(packageName)
                    .as(dtoType.getSimpleName() + "." + field.getName())
                    .doesNotContain(".domain.")
                    .doesNotContain(".infrastructure.");
        }
    }

    @Test
    @DisplayName("OrderCatalogReader는 resolveForOrder 하나만 노출한다")
    void exposesExactlyOneMethod() {
        assertThat(Arrays.stream(OrderCatalogReader.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList())
                .extracting(Method::getName)
                .containsExactly("resolveForOrder");
    }
}
