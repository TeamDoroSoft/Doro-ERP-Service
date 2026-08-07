package com.dorosoft.erp.storeaccess.application.api.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Controller method as genuine user activity that renews the 30-minute idle Session timeout
 * (ADR-02-003). Deliberately opt-in and server-side only: an endpoint not annotated never renews the idle
 * timer, regardless of any Client-supplied header, so future auto-Polling/Health-Check/Telemetry endpoints
 * are exempt by default rather than by exception.
 *
 * <p>Lives in {@code application} (not {@code infrastructure}, where the Session-renewal Interceptor that
 * reads it lives) so Controllers — which may not depend on {@code infrastructure} — can apply it directly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface UserActivity {
}
