package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;

public record CatalogRevisionResponse(long catalogRevision) {

    public static CatalogRevisionResponse from(CatalogRevision revision) {
        return new CatalogRevisionResponse(revision.revision());
    }
}
