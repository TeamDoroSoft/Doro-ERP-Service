package com.dorosoft.erp.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_REQUEST_ID = "doro.erp.requestId";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (!isSafeRequestId(requestId)) {
            requestId = "req-" + UUID.randomUUID();
        }

        request.setAttribute(ATTRIBUTE_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        String previousRequestId = MDC.get("requestId");
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }

    private boolean isSafeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId) || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        return SAFE_REQUEST_ID.matcher(requestId).matches();
    }
}
