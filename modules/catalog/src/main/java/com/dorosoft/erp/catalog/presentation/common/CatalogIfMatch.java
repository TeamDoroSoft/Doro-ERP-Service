package com.dorosoft.erp.catalog.presentation.common;

import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemAwareException;

/** {@code If-Match: "category-42"} 형식의 버전 조건 Header를 해석한다. */
public final class CatalogIfMatch {
    private CatalogIfMatch() {}

    public static long parseVersion(String prefix, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ProblemAwareException(ApiErrorCode.VALIDATION_FAILED, "If-Match 헤더가 필요합니다.");
        }
        String value = ifMatch;
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        String expectedPrefix = prefix + "-";
        if (!value.startsWith(expectedPrefix)) {
            throw new ProblemAwareException(ApiErrorCode.VALIDATION_FAILED, "If-Match 형식이 올바르지 않습니다.");
        }
        try {
            return Long.parseLong(value.substring(expectedPrefix.length()));
        } catch (NumberFormatException ex) {
            throw new ProblemAwareException(ApiErrorCode.VALIDATION_FAILED, "If-Match 형식이 올바르지 않습니다.");
        }
    }
}
