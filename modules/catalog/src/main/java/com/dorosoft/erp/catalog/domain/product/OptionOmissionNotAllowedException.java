package com.dorosoft.erp.catalog.domain.product;

/** 옵션 전체 교체 요청에서 기존 Option ID가 누락됐다(제거 대신 enabled=false를 사용해야 한다). API 오류 코드: 400 OPTION_OMISSION_NOT_ALLOWED. */
public class OptionOmissionNotAllowedException extends RuntimeException {

    public OptionOmissionNotAllowedException(String reason) {
        super(reason);
    }
}
