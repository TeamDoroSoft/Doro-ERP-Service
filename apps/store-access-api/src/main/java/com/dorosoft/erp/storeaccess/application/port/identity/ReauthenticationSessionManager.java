package com.dorosoft.erp.storeaccess.application.port.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;

/**
 * Session-scoped operations for important-action reauthentication (ADR-02-003/011). Failure counting and
 * Session-fixation-defeating ID rotation happen entirely against the current Session, never
 * {@code employee_account} — a Port so the Controller never touches Spring Session directly.
 */
public interface ReauthenticationSessionManager {

    /** Increments and returns the current Session's consecutive-failure count. */
    int recordFailureAndGetCount(HttpServletRequest request);

    /**
     * Resets the failure count, refreshes {@code authenticatedAt} (never {@code absoluteExpiresAt}), and
     * rotates the Session ID — writing the new ID's Cookie onto {@code response}.
     */
    void recordSuccessAndRotate(HttpServletRequest request, HttpServletResponse response, Instant authenticatedAt);

    /** Deletes the current Session outright (5 consecutive reauthentication failures). */
    void invalidateCurrentSession(HttpServletRequest request, HttpServletResponse response);
}
