package com.dorosoft.erp.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IdentitySecurityPropertiesValidator {

    private final DoroErpProperties doroErpProperties;
    private final IdentitySecurityProperties identitySecurityProperties;
    private final DatasourceRuntimeProperties datasourceRuntimeProperties;
    private final RedisRuntimeProperties redisRuntimeProperties;
    private final RedisSessionProperties redisSessionProperties;
    private final Environment environment;

    public IdentitySecurityPropertiesValidator(
            DoroErpProperties doroErpProperties,
            IdentitySecurityProperties identitySecurityProperties,
            DatasourceRuntimeProperties datasourceRuntimeProperties,
            RedisRuntimeProperties redisRuntimeProperties,
            RedisSessionProperties redisSessionProperties,
            Environment environment
    ) {
        this.doroErpProperties = doroErpProperties;
        this.identitySecurityProperties = identitySecurityProperties;
        this.datasourceRuntimeProperties = datasourceRuntimeProperties;
        this.redisRuntimeProperties = redisRuntimeProperties;
        this.redisSessionProperties = redisSessionProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validateOperationalConfiguration() {
        validateIdentityKeyRing(false);

        String[] activeProfiles = environment.getActiveProfiles();
        boolean productionProfile = Arrays.asList(activeProfiles).contains("prod");
        if (!productionProfile) {
            return;
        }

        require("prod".equals(doroErpProperties.getEnvironment()),
                "doro.erp.environment must be prod when the prod profile is active");
        require(!isLocalValue(doroErpProperties.getTenantId()),
                "doro.erp.tenant-id must identify a non-local tenant in production");
        validateProductionUri("doro.erp.public-base-url", doroErpProperties.getPublicBaseUrl());
        for (URI allowedOrigin : doroErpProperties.getAllowedOrigins()) {
            validateProductionUri("doro.erp.allowed-origins", allowedOrigin);
        }

        require(!isLocalJdbcUrl(datasourceRuntimeProperties.getUrl()),
                "spring.datasource.url must not use a local host in production");
        require(!isLocalHost(redisRuntimeProperties.getHost()),
                "spring.data.redis.host must not use a local host in production");
        require(redisRuntimeProperties.getSsl().isEnabled(),
                "spring.data.redis.ssl.enabled must be true in production");
        require(StringUtils.hasText(redisRuntimeProperties.getUsername()),
                "spring.data.redis.username must be set in production");
        require(StringUtils.hasText(redisRuntimeProperties.getPassword()),
                "spring.data.redis.password must be set in production");

        String expectedNamespace = "erp:%s:%s:identity".formatted(
                doroErpProperties.getEnvironment(), doroErpProperties.getTenantId());
        require(expectedNamespace.equals(redisSessionProperties.getNamespace()),
                "spring.session.data.redis.namespace must match the production environment and tenant");

        validateIdentityKeyRing(true);
    }

    private void validateIdentityKeyRing(boolean required) {
        IdentitySecurityProperties.RateLimit rateLimit = identitySecurityProperties.getRateLimit();
        IdentitySecurityProperties.Idempotency idempotency = identitySecurityProperties.getIdempotency();

        validateBase64Key("identity.rate-limit.hmac-key-base64", rateLimit.getHmacKeyBase64(), required);
        validateBase64Key("identity.rate-limit.hmac-previous-key-base64",
                rateLimit.getHmacPreviousKeyBase64(), false);
        require(!StringUtils.hasText(rateLimit.getHmacPreviousKeyBase64())
                        || StringUtils.hasText(rateLimit.getHmacKeyBase64()),
                "identity.rate-limit previous key requires an active key");
        require(!StringUtils.hasText(rateLimit.getHmacPreviousKeyBase64())
                        || !sameDecodedKey(rateLimit.getHmacPreviousKeyBase64(), rateLimit.getHmacKeyBase64()),
                "identity.rate-limit active and previous keys must not be equal");

        boolean previousVersionPresent = StringUtils.hasText(idempotency.getPreviousMasterKeyVersion());
        boolean previousKeyPresent = StringUtils.hasText(idempotency.getPreviousMasterKeyBase64());
        boolean activeVersionPresent = StringUtils.hasText(idempotency.getMasterKeyVersion());
        boolean activeKeyPresent = StringUtils.hasText(idempotency.getMasterKeyBase64());
        require(previousVersionPresent == previousKeyPresent,
                "identity.idempotency previous version and key must be set together");
        require(!activeKeyPresent || activeVersionPresent,
                "identity.idempotency active key requires an active version");
        require(!previousVersionPresent || activeVersionPresent && activeKeyPresent,
                "identity.idempotency previous version and key require an active version and key");

        if (activeVersionPresent) {
            require(idempotency.getMasterKeyVersion().length() <= 50,
                    "identity.idempotency.master-key-version must be 1 to 50 characters");
        } else if (required) {
            throw new IllegalStateException("identity.idempotency.master-key-version must be set in production");
        }

        validateBase64Key("identity.idempotency.master-key-base64",
                idempotency.getMasterKeyBase64(), required);
        validateBase64Key("identity.idempotency.previous-master-key-base64",
                idempotency.getPreviousMasterKeyBase64(), false);

        require(!previousVersionPresent
                        || !idempotency.getMasterKeyVersion().equals(idempotency.getPreviousMasterKeyVersion()),
                "identity.idempotency active and previous versions must not be equal");
        require(!previousKeyPresent
                        || !sameDecodedKey(idempotency.getMasterKeyBase64(),
                                idempotency.getPreviousMasterKeyBase64()),
                "identity.idempotency active and previous keys must not be equal");
        require(!activeKeyPresent || !StringUtils.hasText(rateLimit.getHmacKeyBase64())
                        || !sameDecodedKey(idempotency.getMasterKeyBase64(), rateLimit.getHmacKeyBase64()),
                "identity rate-limit and idempotency active keys must not be equal");
    }

    private void validateBase64Key(String propertyName, String value, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new IllegalStateException(propertyName + " must be set in production");
            }
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(propertyName + " must be valid Base64", exception);
        }
        require(decoded.length >= 32, propertyName + " must decode to at least 32 bytes");
    }

    private void validateProductionUri(String propertyName, URI uri) {
        require(uri != null && "https".equalsIgnoreCase(uri.getScheme()),
                propertyName + " must use https in production");
        String host = uri == null ? null : uri.getHost();
        require(StringUtils.hasText(host) && host.equals(host.toLowerCase(Locale.ROOT)),
                propertyName + " must use a lowercase FQDN in production");
        require(host != null && host.contains(".") && !isIpAddress(host) && !isLocalHost(host),
                propertyName + " must use a non-local FQDN in production");
        require(uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null,
                propertyName + " must not contain credentials, query, or fragment");
    }

    private boolean isLocalJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return true;
        }
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("//localhost")
                || normalized.contains("//127.0.0.1")
                || normalized.contains("//[::1]")
                || normalized.contains("//0.0.0.0");
    }

    private boolean isLocalValue(String value) {
        return !StringUtils.hasText(value)
                || "local".equalsIgnoreCase(value)
                || "local-store".equalsIgnoreCase(value);
    }

    private boolean isLocalHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || "127.0.0.1".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private boolean isIpAddress(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.contains(":");
    }

    private boolean sameDecodedKey(String first, String second) {
        return Arrays.equals(Base64.getDecoder().decode(first), Base64.getDecoder().decode(second));
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
