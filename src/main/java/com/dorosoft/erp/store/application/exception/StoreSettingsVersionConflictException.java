package com.dorosoft.erp.store.application.exception;

import com.dorosoft.erp.platform.web.error.ApiException;
import org.springframework.http.HttpStatus;

public final class StoreSettingsVersionConflictException extends ApiException {

    private final long currentVersion;
    private final long requestedVersion;

    public StoreSettingsVersionConflictException(long currentVersion, long requestedVersion) {
        super("매장 설정 버전이 일치하지 않습니다");
        this.currentVersion = currentVersion;
        this.requestedVersion = requestedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }

    public long requestedVersion() {
        return requestedVersion;
    }

    @Override
    public String code() {
        return "VERSION_CONFLICT";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String detail() {
        return "매장 설정 버전이 일치하지 않습니다. currentVersion="
                + currentVersion
                + ", requestedVersion="
                + requestedVersion;
    }
}
