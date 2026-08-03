package com.dorosoft.erp.catalog.domain.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CatalogRevision 불변식")
class CatalogRevisionTest {

    @Test
    @DisplayName("catalogId가 null이면 NullPointerException")
    void rejectsNullCatalogId() {
        assertThatThrownBy(() -> new CatalogRevision(null, 0L, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("updatedAt이 null이면 NullPointerException")
    void rejectsNullUpdatedAt() {
        assertThatThrownBy(() -> new CatalogRevision(UUID.randomUUID(), 0L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("revision이 음수면 IllegalArgumentException")
    void rejectsNegativeRevision() {
        assertThatThrownBy(() -> new CatalogRevision(UUID.randomUUID(), -1L, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("initial은 revision=0으로 생성한다")
    void initialStartsAtZero() {
        UUID catalogId = UUID.randomUUID();

        CatalogRevision initial = CatalogRevision.initial(catalogId);

        assertThat(initial.catalogId()).isEqualTo(catalogId);
        assertThat(initial.revision()).isZero();
        assertThat(initial.updatedAt()).isNotNull();
    }
}
