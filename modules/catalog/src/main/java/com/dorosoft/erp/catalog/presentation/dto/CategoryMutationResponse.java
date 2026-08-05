package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.category.Category;

/** 변경 응답에는 최신 catalogRevision을 포함한다(API 명세 공통 계약). */
public record CategoryMutationResponse(CategoryResponse category, long catalogRevision) {

    public static CategoryMutationResponse of(Category category, long catalogRevision) {
        return new CategoryMutationResponse(CategoryResponse.from(category), catalogRevision);
    }
}
