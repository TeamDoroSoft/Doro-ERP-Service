package com.dorosoft.erp.identity.presentation.common;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;

/** Reads the request ID established by the platform filter; never manufactures a second ID. */
public final class IdentityRequestId {
    private IdentityRequestId() {
    }

    public static String from(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        throw new IllegalStateException("Request ID filter did not establish a request ID");
    }
}
