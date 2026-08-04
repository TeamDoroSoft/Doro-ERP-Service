package com.dorosoft.erp.catalog.domain.category;

/**
 * 정렬 요청 ID 집합이 중복·누락·다른 소속 없이 현재 대상 전체와 정확히 일치해야 하는 규칙 위반.
 * Category 순서와 Category 안의 Product 순서 모두 이 규칙을 공유한다. API 오류 코드: 400 INVALID_DISPLAY_ORDER.
 */
public class InvalidDisplayOrderException extends RuntimeException {

    public InvalidDisplayOrderException(String reason) {
        super(reason);
    }
}
