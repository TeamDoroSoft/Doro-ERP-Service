package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductMediaJpaRepository extends JpaRepository<ProductMediaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProductMediaEntity m where m.mediaId = :mediaId")
    Optional<ProductMediaEntity> findByIdForUpdate(@Param("mediaId") UUID mediaId);

    List<ProductMediaEntity> findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            MediaStatus status, Instant threshold, Pageable pageable);

    Optional<ProductMediaEntity> findByIdempotencyKey(String idempotencyKey);
}
