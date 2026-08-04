package com.dorosoft.erp.identity.application.error;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import java.util.List;
import java.util.Objects;

/** Feature 01 exception whose externally visible message is selected only from the approved catalogue. */
public class IdentityException extends ProblemAwareException {

    public IdentityException(IdentityErrorCode code) {
        super(Objects.requireNonNull(code, "code"), code.defaultDetail());
    }

    public IdentityException(IdentityErrorCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code"), code.defaultDetail(), List.of(), cause);
    }

    public IdentityException(IdentityErrorCode code, List<ProblemFieldError> fieldErrors) {
        super(Objects.requireNonNull(code, "code"), code.defaultDetail(), fieldErrors);
    }
}
