package com.dorosoft.erp.store.presentation.exception;

import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import java.util.List;

public final class ScheduleValidationFailedException extends ProblemAwareException {

    public ScheduleValidationFailedException(List<ProblemFieldError> fieldErrors) {
        super(ApiErrorCode.VALIDATION_FAILED, "운영 일정 요청 값이 올바르지 않습니다", fieldErrors);
    }
}
