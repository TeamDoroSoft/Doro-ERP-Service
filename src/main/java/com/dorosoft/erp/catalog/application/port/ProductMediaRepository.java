package com.dorosoft.erp.catalog.application.port;

import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMediaRepository {

    Optional<ProductMedia> findById(UUID mediaId);

    /** 완료 처리 동안 동시 완료 요청을 막기 위해 Row를 잠그고 조회한다. */
    Optional<ProductMedia> findByIdForUpdate(UUID mediaId);

    ProductMedia save(ProductMedia media);

    /** 생성 후 PENDING_EXPIRY가 지난 PENDING Media를 정리 대상 순서로 반환한다(만료 정리 Job 전용). */
    List<ProductMedia> findExpiredPending(Instant threshold, int limit);

    /** 이전에 사용된 Idempotency-Key라면 그 결과 Media를 반환한다(생성 API 공통 계약). */
    Optional<ProductMedia> findByIdempotencyKey(String idempotencyKey);
}
