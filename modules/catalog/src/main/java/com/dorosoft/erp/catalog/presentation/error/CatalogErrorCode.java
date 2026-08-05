package com.dorosoft.erp.catalog.presentation.error;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** 기능 03 상품·메뉴 관리 API 명세의 안정된 오류 코드 목록(공통 VALIDATION_FAILED·INTERNAL_SERVER_ERROR는 제외). */
public enum CatalogErrorCode implements ProblemCode {
    INVALID_PRICE("잘못된 금액", HttpStatus.BAD_REQUEST, "상품·옵션 금액이 유효하지 않습니다."),
    INVALID_DISPLAY_ORDER("잘못된 정렬", HttpStatus.BAD_REQUEST, "정렬 ID 목록이 대상 전체와 일치하지 않습니다."),
    INVALID_PRODUCT_OPTIONS("잘못된 상품 옵션", HttpStatus.BAD_REQUEST, "옵션 ID·이름·순서 규칙을 위반했습니다."),
    OPTION_OMISSION_NOT_ALLOWED("옵션 누락 금지", HttpStatus.BAD_REQUEST, "기존 옵션 ID가 누락되었습니다."),
    INVALID_MEDIA_OBJECT("잘못된 미디어 객체", HttpStatus.BAD_REQUEST, "미디어 객체가 존재하지 않거나 형식·크기·Checksum이 유효하지 않습니다."),
    FORBIDDEN("접근 거부", HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),
    CATEGORY_NOT_FOUND("카테고리 없음", HttpStatus.NOT_FOUND, "대상 Category를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND("상품 없음", HttpStatus.NOT_FOUND, "대상 Product를 찾을 수 없습니다."),
    MEDIA_NOT_FOUND("미디어 없음", HttpStatus.NOT_FOUND, "대상 Media를 찾을 수 없습니다."),
    VERSION_CONFLICT("변경 충돌", HttpStatus.CONFLICT, "다른 요청이 먼저 반영되었습니다."),
    IDEMPOTENCY_KEY_REUSED("멱등 키 재사용", HttpStatus.CONFLICT, "다른 요청 내용에 사용된 Idempotency-Key입니다."),
    MEDIA_UPLOAD_CHANGED("업로드 객체 변경", HttpStatus.CONFLICT, "완료 검증 이후 업로드 객체가 변경되었습니다."),
    MEDIA_PUBLISH_CONFLICT("미디어 공개 충돌", HttpStatus.CONFLICT, "기존 공개 객체가 예상 값과 다릅니다."),
    MEDIA_UPLOAD_REJECTED("업로드 거부됨", HttpStatus.CONFLICT, "이미 거부된 업로드입니다."),
    MEDIA_NOT_READY("미디어 준비 안 됨", HttpStatus.CONFLICT, "연결하려는 Media가 아직 준비되지 않았습니다.");

    private final String title;
    private final HttpStatus status;
    private final String defaultDetail;

    CatalogErrorCode(String title, HttpStatus status, String defaultDetail) {
        this.title = title;
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }
}
