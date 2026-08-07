package com.dorosoft.erp.commerce.infrastructure.security;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.application.api.security.ActorType;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Edge 또는 Store Access가 검증한 Actor Context를 서명과 함께 받아 확인하는 인증 Filter.
 *
 * <p>Commerce는 전달된 Header를 무조건 신뢰하지 않는다. 서명이 유효할 때만 Application에 Context를 전달하며
 * 그 밖의 경우 요청은 Actor 없이 진행되고 Application이 401로 거절한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActorContextFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT_ID = "X-Doro-Tenant-Id";
    public static final String HEADER_STORE_ID = "X-Doro-Store-Id";
    public static final String HEADER_ACTOR_TYPE = "X-Doro-Actor-Type";
    public static final String HEADER_ACTOR_ID = "X-Doro-Actor-Id";
    public static final String HEADER_ACTOR_ROLE = "X-Doro-Actor-Role";
    public static final String HEADER_ISSUED_AT = "X-Doro-Actor-Issued-At";
    public static final String HEADER_SIGNATURE = "X-Doro-Actor-Signature";

    private final ActorContextProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ActorContextFilter(ActorContextProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 운영 설정이 누락되면 기동 단계에서 Fail-Closed한다. */
    @PostConstruct
    void verifyConfiguration() {
        if (properties.isSignatureRequired() && !StringUtils.hasText(properties.getSecret())) {
            throw new IllegalStateException(
                    "doro.commerce.actor-context.secret must be configured when signature verification is required");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ActorContext actorContext;
        try {
            actorContext = resolve(request);
        } catch (InvalidActorContextException exception) {
            writeUnauthorized(request, response, exception.getMessage());
            return;
        }

        if (actorContext == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ActorContextHolder.set(actorContext);
        MDC.put("tenantId", actorContext.tenantId().toString());
        MDC.put("actorType", actorContext.actorType().name());
        try {
            filterChain.doFilter(request, response);
        } finally {
            ActorContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("actorType");
        }
    }

    private ActorContext resolve(HttpServletRequest request) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        String storeId = request.getHeader(HEADER_STORE_ID);
        String actorType = request.getHeader(HEADER_ACTOR_TYPE);
        String actorId = request.getHeader(HEADER_ACTOR_ID);
        String role = request.getHeader(HEADER_ACTOR_ROLE);
        String issuedAt = request.getHeader(HEADER_ISSUED_AT);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (!StringUtils.hasText(tenantId)
                && !StringUtils.hasText(actorId)
                && !StringUtils.hasText(role)) {
            return null;
        }

        if (properties.isSignatureRequired()) {
            verifySignature(tenantId, storeId, actorType, actorId, role, issuedAt, signature);
        }

        UUID tenant = parseUuid(tenantId, "tenant");
        UUID actor = parseUuid(actorId, "actor");
        UUID store = StringUtils.hasText(storeId) ? parseUuid(storeId, "store") : null;
        ActorRole actorRole = parseRole(role);
        ActorType type = parseType(actorType);

        if (actorRole.requiredActorType() != type) {
            throw new InvalidActorContextException("actor type does not match role");
        }
        return new ActorContext(tenant, store, type, actor, actorRole);
    }

    private void verifySignature(
            String tenantId,
            String storeId,
            String actorType,
            String actorId,
            String role,
            String issuedAt,
            String signature) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(issuedAt)) {
            throw new InvalidActorContextException("actor context signature is missing");
        }
        long issuedAtMillis;
        try {
            issuedAtMillis = Long.parseLong(issuedAt.trim());
        } catch (NumberFormatException exception) {
            throw new InvalidActorContextException("actor context signature is invalid");
        }
        Duration age = Duration.between(Instant.ofEpochMilli(issuedAtMillis), clock.instant()).abs();
        if (age.compareTo(properties.getMaxAge()) > 0) {
            throw new InvalidActorContextException("actor context signature has expired");
        }
        String expected = ActorContextSignature.sign(
                properties.getSecret(),
                ActorContextSignature.canonical(tenantId, storeId, actorType, actorId, role, issuedAt.trim()));
        if (!ActorContextSignature.matches(expected, signature)) {
            throw new InvalidActorContextException("actor context signature is invalid");
        }
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException exception) {
            throw new InvalidActorContextException(field + " identifier is invalid");
        }
    }

    private ActorRole parseRole(String value) {
        try {
            return ActorRole.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new InvalidActorContextException("actor role is not supported");
        }
    }

    private ActorType parseType(String value) {
        try {
            return ActorType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new InvalidActorContextException("actor type is not supported");
        }
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        // 내부 사유는 로그로만 남기고 응답에는 안정 Code만 노출한다.
        logger.warn("rejected actor context: " + reason);
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        String safeRequestId = requestId instanceof String text && !text.isBlank()
                ? text
                : "req-" + UUID.randomUUID();
        ProblemDetail problemDetail = CatalogErrorCode.AUTHENTICATION_REQUIRED
                .toProblemDetail(safeRequestId, List.of(), "인증 정보를 확인할 수 없습니다.");
        response.setStatus(CatalogErrorCode.AUTHENTICATION_REQUIRED.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }

    private static class InvalidActorContextException extends RuntimeException {
        InvalidActorContextException(String message) {
            super(message);
        }
    }
}
