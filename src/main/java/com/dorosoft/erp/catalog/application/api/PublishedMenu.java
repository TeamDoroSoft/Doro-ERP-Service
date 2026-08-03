package com.dorosoft.erp.catalog.application.api;

import java.util.List;

/** GET /menu가 반환할 공개 메뉴 전체. catalogRevision은 If-None-Match ETag 값으로 쓴다. */
public record PublishedMenu(long catalogRevision, List<PublishedCategory> categories) {}
