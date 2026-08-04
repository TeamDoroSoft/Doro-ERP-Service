package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.store.application.error.StoreErrorCode;

public final class StoreSettingsVersionConflictException extends ProblemAwareException {

    private final long currentVersion;
    private final long requestedVersion;

    public StoreSettingsVersionConflictException(long currentVersion, long requestedVersion) {
        super(StoreErrorCode.VERSION_CONFLICT, detail(currentVersion, requestedVersion));
        this.currentVersion = currentVersion;
        this.requestedVersion = requestedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }

    public long requestedVersion() {
        return requestedVersion;
    }

    private static String detail(long currentVersion, long requestedVersion) {
        return "매장 설정 버전이 일치하지 않습니다. currentVersion="
                + currentVersion
                + ", requestedVersion="
                + requestedVersion;
    }
}
