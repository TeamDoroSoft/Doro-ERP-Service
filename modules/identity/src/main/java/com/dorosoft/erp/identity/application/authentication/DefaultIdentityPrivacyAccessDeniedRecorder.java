package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultIdentityPrivacyAccessDeniedRecorder implements IdentityPrivacyAccessDeniedRecorder {
    private final PrivacyAccessLogger privacyAccessLogger;

    public DefaultIdentityPrivacyAccessDeniedRecorder(PrivacyAccessLogger privacyAccessLogger) {
        this.privacyAccessLogger = privacyAccessLogger;
    }

    @Override
    @Transactional
    public void record(
            DeniedPrivacyOperation operation,
            PrivacyAccessContext encryptedAccessContext
    ) {
        PrivacyAccessRecords.appendDenied(privacyAccessLogger, operation, encryptedAccessContext);
    }
}
