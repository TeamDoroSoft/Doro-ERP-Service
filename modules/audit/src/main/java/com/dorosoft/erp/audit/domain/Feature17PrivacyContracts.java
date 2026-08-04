package com.dorosoft.erp.audit.domain;

import java.util.EnumSet;
import java.util.Set;

public final class Feature17PrivacyContracts {

    public static final Set<PrivacyResourceType> ALLOWED_RESOURCE_TYPES = EnumSet.of(
            PrivacyResourceType.EMPLOYEE_ACCOUNT,
            PrivacyResourceType.IDENTITY_AUDIT_EVENT
    );

    public static final Set<PrivacyResultCode> ALLOWED_RESULT_CODES = EnumSet.allOf(PrivacyResultCode.class);

    public static final Set<PrivacyAccessAction> ALLOWED_ACCESS_ACTIONS = EnumSet.allOf(PrivacyAccessAction.class);

    public static final Set<PrivacyPurposeCode> ALLOWED_PURPOSE_CODES = EnumSet.allOf(PrivacyPurposeCode.class);

    private Feature17PrivacyContracts() {
    }

    public static boolean isAllowedResourceType(PrivacyResourceType resourceType) {
        return ALLOWED_RESOURCE_TYPES.contains(resourceType);
    }

    public static boolean isAllowedResultCode(PrivacyResultCode resultCode) {
        return ALLOWED_RESULT_CODES.contains(resultCode);
    }
}
