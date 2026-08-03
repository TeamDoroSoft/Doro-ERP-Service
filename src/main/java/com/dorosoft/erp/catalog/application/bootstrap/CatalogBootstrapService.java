package com.dorosoft.erp.catalog.application.bootstrap;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배포 직후 catalog_revision 행이 없으면 revision=0으로 한 번 생성한다. */
@Service
public class CatalogBootstrapService {

    private final CatalogRevisionRepository repository;

    public CatalogBootstrapService(CatalogRevisionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CatalogRevision bootstrap() {
        return repository.findCurrent().orElseGet(this::createInitialRevision);
    }

    private CatalogRevision createInitialRevision() {
        return repository.save(CatalogRevision.initial(UUID.randomUUID()));
    }
}
