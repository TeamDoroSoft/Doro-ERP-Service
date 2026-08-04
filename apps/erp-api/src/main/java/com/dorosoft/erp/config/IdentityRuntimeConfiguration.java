package com.dorosoft.erp.config;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.port.AuditReadPort;
import com.dorosoft.erp.audit.application.usecase.DefaultAuditQuery;
import com.dorosoft.erp.audit.application.usecase.DefaultAuditWriter;
import com.dorosoft.erp.audit.infrastructure.HmacAuditCursorCodec;
import com.dorosoft.erp.audit.infrastructure.HmacSha256AuditPayloadSigner;
import com.dorosoft.erp.identity.application.account.IdentityOperationContext;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessContextFactory;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitPort;
import com.dorosoft.erp.identity.application.authentication.IdentityTenant;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.history.IdentityAuditAccessContext;
import com.dorosoft.erp.identity.application.history.DefaultIdentityAuditHistoryQuery;
import com.dorosoft.erp.identity.application.history.HmacIdentityAuditCursorCodec;
import com.dorosoft.erp.identity.application.history.IdentityAuditHistoryQuery;
import com.dorosoft.erp.identity.application.history.IdentitySecurityEventQueryPort;
import com.dorosoft.erp.identity.application.idempotency.UnavailableIdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdempotencyKeyRing;
import com.dorosoft.erp.identity.application.port.PasswordHasher;
import com.dorosoft.erp.identity.infrastructure.ratelimit.IdentityRedisNamespace;
import com.dorosoft.erp.identity.infrastructure.ratelimit.RateLimitKeyRing;
import com.dorosoft.erp.identity.infrastructure.ratelimit.RedisLoginRateLimitAdapter;
import com.dorosoft.erp.identity.infrastructure.ratelimit.UnavailableLoginRateLimitPort;
import com.dorosoft.erp.identity.infrastructure.security.IdentityWebPolicy;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.identity.infrastructure.security.AesGcmIdentityPrivacyAccessContextFactory;
import com.dorosoft.erp.identity.infrastructure.security.TrustedClientIpResolver;
import com.dorosoft.erp.identity.infrastructure.persistence.crypto.Argon2idPasswordHasher;
import com.dorosoft.erp.identity.infrastructure.persistence.crypto.HkdfAesGcmIdempotencyCrypto;
import com.dorosoft.erp.identity.presentation.account.IdentityOperationWebContextFactory;
import com.dorosoft.erp.identity.presentation.common.IdentityRequestId;
import com.dorosoft.erp.identity.presentation.history.IdentityAuditWebContextFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class IdentityRuntimeConfiguration {
    private static final Duration AUDIT_CURSOR_TIME_TO_LIVE = Duration.ofMinutes(15);

    @Bean
    IdentityTenant identityTenant(DoroErpProperties properties) {
        return new IdentityTenant(properties.getTenantId());
    }

    @Bean
    IdentityPrivacyAccessContextFactory identityPrivacyAccessContextFactory(
            AuditSecurityProperties properties
    ) {
        var privacy = properties.getPrivacyClientAddress();
        if (StringUtils.hasText(privacy.getKeyVersion())
                && StringUtils.hasText(privacy.getKeyBase64())) {
            return new AesGcmIdentityPrivacyAccessContextFactory(
                    privacy.getKeyVersion(), decodeBase64(privacy.getKeyBase64()));
        }
        return (tenantId, accessorAccountId, accessorRoleCode, requestId, trustedClientAddress, accessedAt) -> {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE);
        };
    }

    @Bean
    IdentityOperationWebContextFactory identityOperationWebContextFactory(
            IdentityPrivacyAccessContextFactory privacyContextFactory,
            TrustedClientIpResolver clientIpResolver,
            Clock clock
    ) {
        return (authentication, request) -> {
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
                throw new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED);
            }
            String requestId = IdentityRequestId.from(request);
            var privacyContext = privacyContextFactory.create(
                    principal.tenantKey(), principal.accountId(), principal.roleCode(), requestId,
                    clientIpResolver.resolveLiteral(request), clock.instant());
            return new IdentityOperationContext(
                    privacyContext.tenantId(), principal.accountId(), principal.roleCode(), requestId,
                    privacyContext.clientAddressCiphertext(), privacyContext.clientAddressKeyVersion(),
                    privacyContext.accessedAt());
        };
    }

    @Bean
    IdentityAuditWebContextFactory identityAuditWebContextFactory(
            IdentityPrivacyAccessContextFactory privacyContextFactory,
            TrustedClientIpResolver clientIpResolver,
            Clock clock
    ) {
        return (authentication, request) -> {
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
                throw new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED);
            }
            String requestId = IdentityRequestId.from(request);
            var privacyContext = privacyContextFactory.create(
                    principal.tenantKey(), principal.accountId(), principal.roleCode(), requestId,
                    clientIpResolver.resolveLiteral(request), clock.instant());
            return new IdentityAuditAccessContext(
                    privacyContext.tenantId(), principal.accountId(), principal.roleCode(), requestId,
                    privacyContext.clientAddressCiphertext(), privacyContext.clientAddressKeyVersion(),
                    privacyContext.accessedAt());
        };
    }

    @Bean
    IdentityAuditHistoryQuery identityAuditHistoryQuery(
            IdentitySecurityEventQueryPort securityEventQuery,
            AuditQuery auditQuery,
            PrivacyAccessLogger privacyAccessLogger,
            AuditSecurityProperties properties,
            Clock clock
    ) {
        var cursor = properties.getCursorHmac();
        if (StringUtils.hasText(cursor.getKeyBase64())) {
            return new DefaultIdentityAuditHistoryQuery(
                    securityEventQuery,
                    auditQuery,
                    privacyAccessLogger,
                    new HmacIdentityAuditCursorCodec(
                            decodeBase64(cursor.getKeyBase64()),
                            decodeOptionalBase64(cursor.getPreviousKeyBase64()),
                            clock,
                            AUDIT_CURSOR_TIME_TO_LIVE),
                    clock);
        }
        return (filter, accessContext) -> {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE);
        };
    }

    @Bean
    AuditWriter auditWriter(
            AuditAppendPort appendPort,
            AuditSecurityProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        var payload = properties.getPayloadHmac();
        if (StringUtils.hasText(payload.getKeyBase64())) {
            return new DefaultAuditWriter(
                    appendPort,
                    new HmacSha256AuditPayloadSigner(
                            decodeBase64(payload.getKeyBase64()),
                            decodeOptionalBase64(payload.getPreviousKeyBase64())),
                    objectMapper,
                    clock);
        }
        return (command, context) -> {
            throw new AuditContractException(
                    AuditErrorCode.AUDIT_UNAVAILABLE,
                    "Audit runtime key is unavailable");
        };
    }

    @Bean
    AuditQuery auditQuery(
            AuditReadPort readPort,
            AuditSecurityProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        var cursor = properties.getCursorHmac();
        if (StringUtils.hasText(cursor.getKeyBase64())) {
            return new DefaultAuditQuery(
                    readPort,
                    new HmacAuditCursorCodec(
                            decodeBase64(cursor.getKeyBase64()),
                            decodeOptionalBase64(cursor.getPreviousKeyBase64()),
                            clock,
                            AUDIT_CURSOR_TIME_TO_LIVE,
                            objectMapper),
                    clock);
        }
        return new AuditQuery() {
            @Override
            public com.dorosoft.erp.audit.application.api.AuditQueryResult query(
                    String tenantId,
                    com.dorosoft.erp.audit.application.api.AuditQueryFilter filter
            ) {
                throw unavailableAuditQuery();
            }

            @Override
            public Optional<com.dorosoft.erp.audit.application.api.AuditRecordProjection> findById(
                    String tenantId,
                    UUID auditId
            ) {
                throw unavailableAuditQuery();
            }

            private AuditContractException unavailableAuditQuery() {
                return new AuditContractException(
                        AuditErrorCode.AUDIT_UNAVAILABLE,
                        "Audit cursor runtime key is unavailable");
            }
        };
    }

    @Bean
    IdentityWebPolicy identityWebPolicy(DoroErpProperties properties) {
        return new IdentityWebPolicy(properties.getPublicBaseUrl(), properties.getAllowedOrigins());
    }

    @Bean
    IdentityRedisNamespace identityRedisNamespace(RedisSessionProperties properties) {
        return new IdentityRedisNamespace(properties.getNamespace());
    }

    @Bean
    LoginRateLimitPort loginRateLimitPort(
            StringRedisTemplate redisTemplate,
            IdentitySecurityProperties properties,
            IdentityRedisNamespace namespace,
            Clock clock
    ) {
        String activeKey = properties.getRateLimit().getHmacKeyBase64();
        if (!StringUtils.hasText(activeKey)) {
            return new UnavailableLoginRateLimitPort();
        }
        RateLimitKeyRing keyRing = RateLimitKeyRing.fromBase64(
                activeKey,
                properties.getRateLimit().getHmacPreviousKeyBase64()
        );
        return new RedisLoginRateLimitAdapter(redisTemplate, keyRing, namespace, clock);
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new Argon2idPasswordHasher();
    }

    @Bean
    IdempotencyCryptoPort idempotencyCryptoPort(IdentitySecurityProperties properties) {
        IdentitySecurityProperties.Idempotency idempotency = properties.getIdempotency();
        if (!StringUtils.hasText(idempotency.getMasterKeyBase64())) {
            return new UnavailableIdempotencyCryptoPort();
        }
        IdempotencyKeyRing keyRing = new IdempotencyKeyRing(
                idempotency.getMasterKeyVersion(),
                decodeBase64(idempotency.getMasterKeyBase64()),
                emptyToNull(idempotency.getPreviousMasterKeyVersion()),
                decodeOptionalBase64(idempotency.getPreviousMasterKeyBase64())
        );
        return new HkdfAesGcmIdempotencyCrypto(keyRing);
    }

    private static byte[] decodeOptionalBase64(String value) {
        return StringUtils.hasText(value) ? decodeBase64(value) : null;
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
