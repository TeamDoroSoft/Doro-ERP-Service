package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductObjectStorage;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 생성 후 24시간이 지난 PENDING Media를 REJECTED로 전환하고 Staging Object를 정리한다(ADR-007). */
@Service
public class MediaExpirySweepService {

    private static final int BATCH_LIMIT = 200;

    private final ProductMediaRepository repository;
    private final ProductObjectStorage storage;

    public MediaExpirySweepService(ProductMediaRepository repository, ProductObjectStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /** 이번 호출에서 REJECTED로 전환한 건수를 반환한다. 배치 상한을 넘는 잔여분은 다음 호출이 처리한다. */
    @Transactional
    public int rejectExpiredPending() {
        Instant threshold = Instant.now().minus(ProductMedia.PENDING_EXPIRY);
        List<ProductMedia> expired = repository.findExpiredPending(threshold, BATCH_LIMIT);
        for (ProductMedia media : expired) {
            repository.save(media.reject());
            storage.deleteStagingObjectBestEffort(media.mediaId());
        }
        return expired.size();
    }
}
