package com.dorosoft.erp.catalog.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CatalogRevisionJpaRepository extends JpaRepository<CatalogRevisionEntity, UUID> {

    /**
     * revision을 원자적으로 1 증가시키는 DB 레벨 UPDATE다. 낙관적 잠금(@Version) 비교를 거치지 않아
     * 서로 무관한 여러 변경이 동시에 호출해도 거짓 충돌로 서로를 실패시키지 않는다(MySQL 행 잠금으로
     * 순서만 직렬화된다). clearAutomatically로 이 Bulk Update 이후의 조회가 1차 캐시의 낡은 값이
     * 아니라 DB의 최신 값을 다시 읽도록 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CatalogRevisionEntity e SET e.revision = e.revision + 1, e.updatedAt = :updatedAt")
    int bumpRevision(@Param("updatedAt") Instant updatedAt);
}
