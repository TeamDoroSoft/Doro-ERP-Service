package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

/** Session attribute keys for employee-authenticated Sessions (ADR-02-003/011). */
public final class EmployeeSessionAttributes {

    /** Last successful password authentication (login or reauthentication); drives the 15-minute reauth window. */
    public static final String AUTHENTICATED_AT = "doro.identity.authenticatedAt";

    /** Fixed at login; never extended by activity or reauthentication (ADR-02-003). */
    public static final String ABSOLUTE_EXPIRES_AT = "doro.identity.absoluteExpiresAt";

    /** Consecutive reauthentication failures in the current Session (ADR-02-011); resets on success. */
    public static final String REAUTHENTICATION_FAILURE_COUNT = "doro.identity.reauthenticationFailureCount";

    private EmployeeSessionAttributes() {
    }
}
