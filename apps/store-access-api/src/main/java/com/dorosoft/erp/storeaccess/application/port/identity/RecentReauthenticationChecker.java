package com.dorosoft.erp.storeaccess.application.port.identity;

/**
 * Enforces the ADR-02-003/011 15-minute recent-reauthentication requirement for important management
 * actions, checked inside the Application Service itself (not just the Controller/Frontend), against the
 * current request's Session — a Port since only {@code infrastructure} may read the Session directly.
 */
public interface RecentReauthenticationChecker {

    /** Throws a {@code 403 REAUTHENTICATION_REQUIRED} ProblemAwareException if not satisfied. */
    void requireRecentReauthentication();
}
