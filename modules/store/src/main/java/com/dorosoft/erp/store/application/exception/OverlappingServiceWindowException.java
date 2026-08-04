package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import com.dorosoft.erp.store.application.error.StoreErrorCode;
import com.dorosoft.erp.store.domain.schedule.OperatingScheduleViolationException;
import java.util.List;

public final class OverlappingServiceWindowException extends ProblemAwareException {

    public OverlappingServiceWindowException(OperatingScheduleViolationException cause) {
        super(StoreErrorCode.OVERLAPPING_SERVICE_WINDOW, cause.getMessage(),
                List.of(new ProblemFieldError(cause.field(), cause.reason().name())), cause);
    }
}
