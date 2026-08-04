package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TableQrPublicRateLimiter {

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int capacity;
    private final long windowMillis;
    private final Clock clock;

    TableQrPublicRateLimiter(
            @Value("${doro.table.qr-access.rate-limit.capacity:60}") int capacity,
            @Value("${doro.table.qr-access.rate-limit.window:PT1M}") Duration window,
            Clock clock) {
        this.capacity = Math.max(1, capacity);
        this.windowMillis = Math.max(1, window.toMillis());
        this.clock = clock;
    }

    void verify(HttpServletRequest request) {
        long now = clock.millis();
        Window window = windows.compute(key(request), (ignored, current) -> nextWindow(current, now));
        if (!window.allowed()) {
            throw new TableManagementException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    TableErrorCode.QR_RATE_LIMITED,
                    "QR access is not available.");
        }
    }

    private Window nextWindow(Window current, long now) {
        if (current == null || now - current.startedAtMillis() >= windowMillis) {
            return new Window(now, 1, true);
        }
        if (current.count() >= capacity) {
            return new Window(current.startedAtMillis(), current.count(), false);
        }
        return new Window(current.startedAtMillis(), current.count() + 1, true);
    }

    private static String key(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private record Window(long startedAtMillis, int count, boolean allowed) {}
}
