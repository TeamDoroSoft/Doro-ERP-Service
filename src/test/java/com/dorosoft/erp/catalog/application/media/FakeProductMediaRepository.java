package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트 전용 In-memory ProductMediaRepository. Pessimistic Lock은 흉내 내지 않고 단순 조회로 대체한다. */
class FakeProductMediaRepository implements ProductMediaRepository {

    private final Map<UUID, ProductMedia> store = new LinkedHashMap<>();

    @Override
    public Optional<ProductMedia> findById(UUID mediaId) {
        return Optional.ofNullable(store.get(mediaId));
    }

    @Override
    public Optional<ProductMedia> findByIdForUpdate(UUID mediaId) {
        return findById(mediaId);
    }

    @Override
    public ProductMedia save(ProductMedia media) {
        store.put(media.mediaId(), media);
        return media;
    }

    @Override
    public List<ProductMedia> findExpiredPending(Instant threshold, int limit) {
        return store.values().stream()
                .filter(ProductMedia::isPending)
                .filter(media -> !media.createdAt().isAfter(threshold))
                .sorted(Comparator.comparing(ProductMedia::createdAt))
                .limit(limit)
                .toList();
    }
}
