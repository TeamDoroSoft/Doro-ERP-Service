package com.dorosoft.erp.catalog.presentation.common;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;

/** platform 공통 Filter가 확정한 requestId를 읽는다. 별도로 새 값을 만들지 않는다. */
public final class CatalogRequestId {
    private CatalogRequestId() {}

    public static String from(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        throw new IllegalStateException("Request ID filter did not establish a request ID");
    }
}
