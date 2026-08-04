package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.store.application.error.StoreErrorCode;

public final class StoreNotInitializedException extends ProblemAwareException {

    public StoreNotInitializedException() {
        super(StoreErrorCode.STORE_NOT_INITIALIZED, "매장 설정이 초기화되지 않았습니다");
    }
}
