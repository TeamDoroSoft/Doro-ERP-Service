package com.dorosoft.erp.store.presentation.exception;

import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import java.util.List;

public final class IfMatchMissingOrInvalidException extends ProblemAwareException {

    public IfMatchMissingOrInvalidException() {
        super(
                ApiErrorCode.VALIDATION_FAILED,
                "If-Match 헤더가 필요하며 유효한 버전이어야 합니다",
                List.of(new ProblemFieldError("If-Match", "REQUIRED")));
    }
}
