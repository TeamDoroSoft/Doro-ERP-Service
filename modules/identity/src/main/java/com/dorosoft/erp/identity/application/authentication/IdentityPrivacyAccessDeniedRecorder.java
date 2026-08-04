package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;

/** Narrow callback used by web authorization handling for authenticated PII denials only. */
public interface IdentityPrivacyAccessDeniedRecorder {
    void record(
            DeniedPrivacyOperation operation,
            PrivacyAccessContext encryptedAccessContext
    );
}
