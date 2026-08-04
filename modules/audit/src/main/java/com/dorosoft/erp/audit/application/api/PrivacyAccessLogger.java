package com.dorosoft.erp.audit.application.api;

public interface PrivacyAccessLogger {

    PrivacyAccessResult append(PrivacyAccessCommand command, PrivacyAccessContext context);
}
