package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import java.time.Duration;

/** Injectable so tests can skip real waiting between {@link SessionInvalidator} retries. */
@FunctionalInterface
public interface RetryBackoffSleeper {

    void sleep(Duration duration) throws InterruptedException;

    static RetryBackoffSleeper threadSleep() {
        return duration -> Thread.sleep(duration.toMillis());
    }
}
