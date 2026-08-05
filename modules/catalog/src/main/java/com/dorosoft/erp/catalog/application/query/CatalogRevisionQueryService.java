package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 변경 응답에 포함할 최신 catalogRevision 조회(API 명세 공통 계약). */
@Service
public class CatalogRevisionQueryService {

    private final CatalogRevisionRepository catalogRevisionRepository;

    public CatalogRevisionQueryService(CatalogRevisionRepository catalogRevisionRepository) {
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional(readOnly = true)
    public long currentRevision() {
        return catalogRevisionRepository
                .findCurrent()
                .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                .revision();
    }
}
