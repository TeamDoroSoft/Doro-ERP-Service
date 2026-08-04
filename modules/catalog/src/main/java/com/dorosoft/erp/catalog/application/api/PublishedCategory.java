package com.dorosoft.erp.catalog.application.api;

import java.util.List;
import java.util.UUID;

/** 공개 메뉴의 카테고리 Projection. 판매 활성 상품이 하나도 없는 Category는 결과에 포함되지 않는다. */
public record PublishedCategory(UUID categoryId, String name, List<PublishedProduct> products) {}
