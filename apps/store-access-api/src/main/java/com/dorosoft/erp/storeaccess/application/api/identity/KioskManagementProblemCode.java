package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemCode;
import org.springframework.http.HttpStatus;

/** Error contract for Kiosk device registration/rotation/revocation (ADR-02-013). */
public enum KioskManagementProblemCode implements ProblemCode {

    KIOSK_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Kiosk 기기 관리 권한이 없습니다."),
    KIOSK_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Kiosk 기기를 찾을 수 없습니다."),
    KIOSK_DEVICE_NOT_ACTIVE(HttpStatus.CONFLICT, "폐기된 Kiosk 기기는 Secret을 회전할 수 없습니다.");

    private final HttpStatus status;
    private final String title;

    KioskManagementProblemCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
