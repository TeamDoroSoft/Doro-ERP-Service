package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

public final class HostOriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final Authority expectedHost;
    private final Set<Origin> allowedOrigins;
    private final SecurityProblemWriter problemWriter;

    public HostOriginValidationFilter(
            IdentityWebPolicy policy,
            SecurityProblemWriter problemWriter
    ) {
        this.expectedHost = Authority.fromPublicBaseUrl(policy.publicBaseUrl());
        this.allowedOrigins = policy.allowedOrigins().stream()
                .map(Origin::fromConfiguredUri)
                .collect(Collectors.toUnmodifiableSet());
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("doro.erp.allowed-origins must not be empty");
        }
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!hasExpectedHost(request)) {
            problemWriter.write(request, response, 403, "FORBIDDEN", "접근 거부",
                    "요청을 처리할 수 없습니다.");
            return;
        }

        if (STATE_CHANGING_METHODS.contains(request.getMethod()) && !hasAllowedOrigin(request)) {
            problemWriter.write(request, response, 403, "CSRF_TOKEN_INVALID", "요청 검증 실패",
                    "요청 검증 정보가 유효하지 않습니다.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasExpectedHost(HttpServletRequest request) {
        List<String> hostHeaders = Collections.list(request.getHeaders("Host"));
        if (hostHeaders.size() != 1) {
            return false;
        }
        try {
            return expectedHost.matches(Authority.fromHostHeader(hostHeaders.getFirst()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasAllowedOrigin(HttpServletRequest request) {
        List<String> origins = Collections.list(request.getHeaders("Origin"));
        if (origins.size() != 1 || "null".equals(origins.getFirst())) {
            return false;
        }
        try {
            return allowedOrigins.contains(Origin.fromRequestHeader(origins.getFirst()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record Origin(String scheme, String host, int port) {

        private static Origin fromConfiguredUri(URI uri) {
            if (uri == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !isRootPath(uri.getRawPath())) {
                throw new IllegalStateException("doro.erp.allowed-origins contains an invalid origin");
            }
            return from(uri, "doro.erp.allowed-origins contains an invalid origin");
        }

        private static Origin fromRequestHeader(String value) {
            URI uri = URI.create(value);
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !isRootPath(uri.getRawPath())) {
                throw new IllegalArgumentException("Origin must not include path or credentials");
            }
            return from(uri, "Invalid Origin");
        }

        private static Origin from(URI uri, String errorMessage) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || !(scheme.equalsIgnoreCase("https")
                    || scheme.equalsIgnoreCase("http"))) {
                throw new IllegalArgumentException(errorMessage);
            }
            return new Origin(scheme.toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT),
                    effectivePort(scheme, uri.getPort()));
        }

        private static boolean isRootPath(String path) {
            return path == null || path.isEmpty() || "/".equals(path);
        }
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
            if (parsed.getHost() == null || parsed.getRawUserInfo() != null || parsed.getRawPath().length() > 0) {
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
