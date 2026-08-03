package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import org.springframework.http.HttpStatus;

public final class StoreNotInitializedException extends ApiException {

    @Override
    public String code() {
        return "STORE_NOT_INITIALIZED";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String detail() {
        return "매장 설정이 초기화되지 않았습니다";
    }
}
