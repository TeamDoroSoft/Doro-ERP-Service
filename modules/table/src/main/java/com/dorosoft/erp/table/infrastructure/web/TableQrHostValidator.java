package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TableQrHostValidator {

    private final Authority expectedHost;

    TableQrHostValidator(@Value("${doro.erp.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.expectedHost = Authority.fromPublicBaseUrl(URI.create(publicBaseUrl));
    }

    void verify(HttpServletRequest request) {
        List<String> hosts = Collections.list(request.getHeaders("Host"));
        if (hosts.size() != 1) {
            throw forbidden();
        }
        try {
            if (!expectedHost.matches(Authority.fromHostHeader(hosts.getFirst()))) {
                throw forbidden();
            }
        } catch (IllegalArgumentException exception) {
            throw forbidden();
        }
    }

    private static TableManagementException forbidden() {
        return new TableManagementException(
                HttpStatus.FORBIDDEN,
                TableErrorCode.QR_HOST_FORBIDDEN,
                "QR access is not available.");
    }

    private record Authority(String host, int port) {

        private static Authority fromPublicBaseUrl(URI uri) {
            if (uri == null || uri.getHost() == null || uri.getScheme() == null) {
                throw new IllegalStateException("doro.erp.public-base-url must contain a host");
            }
            return new Authority(uri.getHost().toLowerCase(Locale.ROOT), effectivePort(uri.getScheme(), uri.getPort()));
        }

        private static Authority fromHostHeader(String value) {
            if (value == null || value.isBlank() || value.indexOf('/') >= 0 || value.indexOf('@') >= 0) {
                throw new IllegalArgumentException("Invalid Host");
            }
            URI parsed = URI.create("http://" + value);
            if (parsed.getHost() == null || parsed.getRawUserInfo() != null || !parsed.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("Invalid Host");
            }
            return new Authority(parsed.getHost().toLowerCase(Locale.ROOT), parsed.getPort());
        }

        private boolean matches(Authority requestHost) {
            if (!host.equals(requestHost.host)) {
                return false;
            }
            return requestHost.port == -1 || requestHost.port == port;
        }
    }

    private static int effectivePort(String scheme, int explicitPort) {
        if (explicitPort != -1) {
            return explicitPort;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
