package com.dorosoft.erp.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dorosoft.erp.audit.application.api.AuditQuery;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.port.AuditReadPort;
import com.dorosoft.erp.audit.application.usecase.DefaultAuditQuery;
import com.dorosoft.erp.audit.application.usecase.DefaultAuditWriter;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.history.DefaultIdentityAuditHistoryQuery;
import com.dorosoft.erp.identity.application.history.IdentitySecurityEventQueryPort;
import com.dorosoft.erp.identity.infrastructure.security.AesGcmIdentityPrivacyAccessContextFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdentityRuntimeConfigurationTest {
    private final IdentityRuntimeConfiguration configuration = new IdentityRuntimeConfiguration();
    private final Clock clock = Clock.systemUTC();

    @Test
    void wiresApprovedFeature17ImplementationsWhenActiveKeysArePresent() {
        AuditSecurityProperties properties = properties();
        var privacyFactory = configuration.identityPrivacyAccessContextFactory(properties);
        var writer = configuration.auditWriter(
                mock(AuditAppendPort.class), properties, new ObjectMapper(), clock);
        AuditQuery auditQuery = configuration.auditQuery(
                mock(AuditReadPort.class), properties, new ObjectMapper(), clock);
        var history = configuration.identityAuditHistoryQuery(
                mock(IdentitySecurityEventQueryPort.class),
                auditQuery,
                mock(PrivacyAccessLogger.class),
                properties,
                clock);

        assertInstanceOf(AesGcmIdentityPrivacyAccessContextFactory.class, privacyFactory);
        assertInstanceOf(DefaultAuditWriter.class, writer);
        assertInstanceOf(DefaultAuditQuery.class, auditQuery);
        assertInstanceOf(DefaultIdentityAuditHistoryQuery.class, history);
    }

    @Test
    void keepsPrivacyBearingCallsFailClosedWhenLocalKeysAreAbsent() {
        var privacyFactory = configuration.identityPrivacyAccessContextFactory(
                new AuditSecurityProperties());

        assertThrows(IdentityException.class, () -> privacyFactory.create(
                "local-store",
                UUID.randomUUID(),
                "ADMIN",
                "req-1",
                "127.0.0.1",
                Instant.now()));
    }

    private AuditSecurityProperties properties() {
        AuditSecurityProperties properties = new AuditSecurityProperties();
        properties.getPayloadHmac().setKeyBase64(key((byte) 1));
        properties.getCursorHmac().setKeyBase64(key((byte) 2));
        properties.getCursorHmac().setPreviousKeyBase64(key((byte) 3));
        properties.getPrivacyClientAddress().setKeyVersion("v1");
        properties.getPrivacyClientAddress().setKeyBase64(key((byte) 4));
        return properties;
    }

    private String key(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
