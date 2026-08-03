package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.Optional;

/** 테스트 전용 In-memory CatalogRevisionRepository. */
class FakeCatalogRevisionRepository implements CatalogRevisionRepository {

    private CatalogRevision current;

    FakeCatalogRevisionRepository(CatalogRevision initial) {
        this.current = initial;
    }

    @Override
    public Optional<CatalogRevision> findCurrent() {
        return Optional.ofNullable(current);
    }

    @Override
    public CatalogRevision save(CatalogRevision revision) {
        this.current = revision;
        return revision;
    }

    @Override
    public boolean exists() {
        return current != null;
    }
}
