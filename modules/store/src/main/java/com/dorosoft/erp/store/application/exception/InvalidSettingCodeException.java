package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import com.dorosoft.erp.store.application.error.StoreErrorCode;
import java.util.List;

public final class InvalidSettingCodeException extends ProblemAwareException {

    public InvalidSettingCodeException(String detail, List<ProblemFieldError> fieldErrors) {
        super(StoreErrorCode.INVALID_SETTING_CODE, detail, fieldErrors);
    }
}
