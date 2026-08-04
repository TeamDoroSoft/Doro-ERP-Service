package com.dorosoft.erp.audit.application.api;

import java.util.UUID;

public record PrivacyAccessSubject(
        String subjectType,
        UUID subjectId
) {
}
