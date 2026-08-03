package com.dorosoft.erp.catalog.domain.revision;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalog 전체 정렬 변경의 동시성 기준. 업체 Schema당 한 행만 존재한다.
 * revision 자체가 낙관적 잠금 값이며, 값 변경 없이 저장을 반복해도(Touch) 증가한다.
 */
public record CatalogRevision(UUID catalogId, long revision, Instant updatedAt) {

    public CatalogRevision {
        Objects.requireNonNull(catalogId, "catalogId는 필수다");
        Objects.requireNonNull(updatedAt, "updatedAt은 필수다");
        if (revision < 0) {
            throw new IllegalArgumentException("revision은 0 이상이어야 한다");
        }
    }

    /** 업체 배포 시 최초 생성하는 revision=0 상태. */
    public static CatalogRevision initial(UUID catalogId) {
        return new CatalogRevision(catalogId, 0L, Instant.now());
    }
}
