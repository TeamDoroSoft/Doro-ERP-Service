package com.dorosoft.erp.identity.application.authentication;

import jakarta.servlet.http.HttpServletRequest;

public interface IdentityDeniedPrivacyAccessHandler {
    boolean recordIfRequired(HttpServletRequest request);
}
