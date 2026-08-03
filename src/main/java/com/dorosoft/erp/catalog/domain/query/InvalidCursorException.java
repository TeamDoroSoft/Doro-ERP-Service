package com.dorosoft.erp.catalog.domain.query;

/** 관리자 목록 조회의 cursor 값이 서버가 발급한 형식이 아니다. API 오류 코드: 400 VALIDATION_FAILED. */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("유효하지 않은 cursor입니다: " + cursor);
    }
}
