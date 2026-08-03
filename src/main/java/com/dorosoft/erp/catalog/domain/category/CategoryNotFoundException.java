package com.dorosoft.erp.catalog.domain.category;

import java.util.UUID;

/** 현재 업체에 존재하지 않는 categoryId 요청. API 오류 코드: 404 CATEGORY_NOT_FOUND. */
public class CategoryNotFoundException extends RuntimeException {

    private final UUID categoryId;

    public CategoryNotFoundException(UUID categoryId) {
        super("Category를 찾을 수 없습니다. categoryId=" + categoryId);
        this.categoryId = categoryId;
    }

    public UUID categoryId() {
        return categoryId;
    }
}
