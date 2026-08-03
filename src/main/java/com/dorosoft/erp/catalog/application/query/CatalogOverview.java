package com.dorosoft.erp.catalog.application.query;

import java.util.List;

/** GET /catalog가 반환할 관리 화면용 전체 Catalog. Entity version과 Catalog Revision을 그대로 포함한다. */
public record CatalogOverview(long catalogRevision, List<CategoryOverview> categories) {}
