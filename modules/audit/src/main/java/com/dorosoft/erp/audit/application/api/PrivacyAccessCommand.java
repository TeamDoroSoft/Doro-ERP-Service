package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.PrivacyAccessAction;
import com.dorosoft.erp.audit.domain.PrivacyPurposeCode;
import com.dorosoft.erp.audit.domain.PrivacyResourceType;
import com.dorosoft.erp.audit.domain.PrivacyResultCode;

import java.util.Collections;
import java.util.List;

public record PrivacyAccessCommand(
        PrivacyResourceType resourceType,
        PrivacyAccessAction accessAction,
        PrivacyPurposeCode purposeCode,
        PrivacyResultCode resultCode,
        List<PrivacyAccessSubject> subjects,
        int resultCount
) {

    public PrivacyAccessCommand {
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType must not be null");
        }
        if (accessAction == null) {
            throw new IllegalArgumentException("accessAction must not be null");
        }
        if (purposeCode == null) {
            throw new IllegalArgumentException("purposeCode must not be null");
        }
        if (resultCode == null) {
            throw new IllegalArgumentException("resultCode must not be null");
        }

        subjects = subjects == null ? Collections.emptyList() : List.copyOf(subjects);
    }

    /** Fixed Feature 01 contract for successful Identity audit-event reads. */
    public static PrivacyAccessCommand identityAuditEventRead(List<PrivacyAccessSubject> subjects) {
        return identityAuditEventRead(PrivacyResultCode.SUCCESS, subjects);
    }

    /** Fixed Feature 01 contract for denied Identity audit-event reads. */
    public static PrivacyAccessCommand identityAuditEventReadDenied(List<PrivacyAccessSubject> subjects) {
        return identityAuditEventRead(PrivacyResultCode.DENIED, subjects);
    }

    private static PrivacyAccessCommand identityAuditEventRead(
            PrivacyResultCode result,
            List<PrivacyAccessSubject> subjects
    ) {
        List<PrivacyAccessSubject> safeSubjects = subjects == null ? List.of() : List.copyOf(subjects);
        return new PrivacyAccessCommand(
                PrivacyResourceType.IDENTITY_AUDIT_EVENT,
                PrivacyAccessAction.READ,
                PrivacyPurposeCode.SECURITY_INVESTIGATION,
                result,
                safeSubjects,
                safeSubjects.size()
        );
    }

    /** Fixed Feature 01 contract for a successful employee-account read. */
    public static PrivacyAccessCommand employeeAccountRead(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.READ, PrivacyResultCode.SUCCESS, subjects);
    }

    /** Fixed Feature 01 contract for a successful employee-account creation. */
    public static PrivacyAccessCommand employeeAccountCreate(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.CREATE, PrivacyResultCode.SUCCESS, subjects);
    }

    /** Fixed Feature 01 contract for a successful employee-account update. */
    public static PrivacyAccessCommand employeeAccountUpdate(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.UPDATE, PrivacyResultCode.SUCCESS, subjects);
    }

    /** Fixed Feature 01 contract for a denied employee-account read. */
    public static PrivacyAccessCommand employeeAccountReadDenied(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.READ, PrivacyResultCode.DENIED, subjects);
    }

    /** Fixed Feature 01 contract for a denied employee-account creation. */
    public static PrivacyAccessCommand employeeAccountCreateDenied(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.CREATE, PrivacyResultCode.DENIED, subjects);
    }

    /** Fixed Feature 01 contract for a denied employee-account update. */
    public static PrivacyAccessCommand employeeAccountUpdateDenied(List<PrivacyAccessSubject> subjects) {
        return employeeAccount(PrivacyAccessAction.UPDATE, PrivacyResultCode.DENIED, subjects);
    }

    private static PrivacyAccessCommand employeeAccount(
            PrivacyAccessAction action,
            PrivacyResultCode result,
            List<PrivacyAccessSubject> subjects
    ) {
        List<PrivacyAccessSubject> safeSubjects = subjects == null ? List.of() : List.copyOf(subjects);
        return new PrivacyAccessCommand(
                PrivacyResourceType.EMPLOYEE_ACCOUNT,
                action,
                PrivacyPurposeCode.STORE_OPERATION,
                result,
                safeSubjects,
                safeSubjects.size()
        );
    }
}
