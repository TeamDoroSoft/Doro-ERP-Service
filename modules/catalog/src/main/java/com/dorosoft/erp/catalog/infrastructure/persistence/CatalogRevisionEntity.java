package com.dorosoft.erp.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Catalog 전체 정렬 동시성 기준의 JPA 매핑. revision 컬럼이 곧 낙관적 잠금 값이다. */
@Entity
@Table(name = "catalog_revision")
class CatalogRevisionEntity {

    @Id
    @Column(name = "catalog_id", nullable = false)
    private UUID catalogId;

    @Version
    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatalogRevisionEntity() {}

    CatalogRevisionEntity(UUID catalogId) {
        this.catalogId = catalogId;
        this.updatedAt = Instant.now();
    }

    UUID getCatalogId() {
        return catalogId;
    }

    long getRevision() {
        return revision;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 값 변경 없이도 Row를 Dirty로 만들어 저장 시 @Version(revision)이 증가하게 한다. */
    void touch() {
        this.updatedAt = Instant.now();
    }
}
