package com.dorosoft.erp.catalog.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CatalogRevisionRepository Port 구조 제약(업체 Schema 격리)")
class CatalogRevisionRepositoryContractTest {

    private static final String RULE =
            "배포당 DataSource 1개가 이미 해당 업체 Schema를 가리키므로"
                    + " Repository Port는 업체 ID·Schema명을 파라미터로 받으면 안 된다. 위반: ";

    private static List<Method> declaredMethods() {
        return Arrays.stream(CatalogRevisionRepository.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .toList();
    }

    @Test
    @DisplayName("어떤 메서드도 UUID·String 파라미터를 받지 않는다")
    void noMethodTakesTenantIdentifierParameter() {
        for (Method method : declaredMethods()) {
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType)
                        .as(RULE + method.getName() + "(" + parameterType.getName() + ")")
                        .isNotEqualTo(UUID.class)
                        .isNotEqualTo(String.class);
            }
        }
    }

    @Test
    @DisplayName("Port는 findCurrent·save·exists 3개 메서드만 노출한다")
    void exposesExactlyThreeMethods() {
        assertThat(declaredMethods()).extracting(Method::getName)
                .containsExactlyInAnyOrder("findCurrent", "save", "exists");
    }

    @Test
    @DisplayName("각 메서드의 시그니처가 계약대로다")
    void signaturesMatchContract() throws NoSuchMethodException {
        Method findCurrent = CatalogRevisionRepository.class.getDeclaredMethod("findCurrent");
        Method exists = CatalogRevisionRepository.class.getDeclaredMethod("exists");
        Method save =
                CatalogRevisionRepository.class.getDeclaredMethod(
                        "save", com.dorosoft.erp.catalog.domain.revision.CatalogRevision.class);

        assertThat(findCurrent.getParameterCount()).isZero();
        assertThat(findCurrent.getReturnType()).isEqualTo(Optional.class);
        assertThat(exists.getParameterCount()).isZero();
        assertThat(exists.getReturnType()).isEqualTo(boolean.class);
        assertThat(save.getReturnType())
                .isEqualTo(com.dorosoft.erp.catalog.domain.revision.CatalogRevision.class);
    }
}
