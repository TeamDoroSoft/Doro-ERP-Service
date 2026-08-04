package com.dorosoft.erp.audit.application.port;

import com.dorosoft.erp.audit.application.model.PrivacyAccessRecord;

public interface PrivacyAccessAppendPort {
    void append(PrivacyAccessRecord record);
}
