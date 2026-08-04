package com.dorosoft.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Feature 17 Audit/Privacy runtime key binding. Secret values are never exposed from this type. */
@ConfigurationProperties(prefix = "audit.security")
@Validated
public class AuditSecurityProperties {
    private final KeyRing payloadHmac = new KeyRing();
    private final KeyRing cursorHmac = new KeyRing();
    private final PrivacyClientAddress privacyClientAddress = new PrivacyClientAddress();

    public KeyRing getPayloadHmac() {
        return payloadHmac;
    }

    public KeyRing getCursorHmac() {
        return cursorHmac;
    }

    public PrivacyClientAddress getPrivacyClientAddress() {
        return privacyClientAddress;
    }

    public static final class KeyRing {
        private String keyBase64 = "";
        private String previousKeyBase64 = "";

        public String getKeyBase64() {
            return keyBase64;
        }

        public void setKeyBase64(String keyBase64) {
            this.keyBase64 = keyBase64;
        }

        public String getPreviousKeyBase64() {
            return previousKeyBase64;
        }

        public void setPreviousKeyBase64(String previousKeyBase64) {
            this.previousKeyBase64 = previousKeyBase64;
        }
    }

    public static final class PrivacyClientAddress {
        private String keyVersion = "";
        private String keyBase64 = "";

        public String getKeyVersion() {
            return keyVersion;
        }

        public void setKeyVersion(String keyVersion) {
            this.keyVersion = keyVersion;
        }

        public String getKeyBase64() {
            return keyBase64;
        }

        public void setKeyBase64(String keyBase64) {
            this.keyBase64 = keyBase64;
        }
    }
}
