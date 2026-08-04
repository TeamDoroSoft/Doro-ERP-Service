package com.dorosoft.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "identity")
@Validated
public class IdentitySecurityProperties {

    private final RateLimit rateLimit = new RateLimit();
    private final Idempotency idempotency = new Idempotency();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    @Validated
    public static final class RateLimit {

        private String hmacKeyBase64 = "";
        private String hmacPreviousKeyBase64 = "";

        public String getHmacKeyBase64() {
            return hmacKeyBase64;
        }

        public void setHmacKeyBase64(String hmacKeyBase64) {
            this.hmacKeyBase64 = hmacKeyBase64;
        }

        public String getHmacPreviousKeyBase64() {
            return hmacPreviousKeyBase64;
        }

        public void setHmacPreviousKeyBase64(String hmacPreviousKeyBase64) {
            this.hmacPreviousKeyBase64 = hmacPreviousKeyBase64;
        }
    }

    @Validated
    public static final class Idempotency {
        private String masterKeyVersion = "";

        private String masterKeyBase64 = "";
        private String previousMasterKeyVersion = "";
        private String previousMasterKeyBase64 = "";

        public String getMasterKeyVersion() {
            return masterKeyVersion;
        }

        public void setMasterKeyVersion(String masterKeyVersion) {
            this.masterKeyVersion = masterKeyVersion;
        }

        public String getMasterKeyBase64() {
            return masterKeyBase64;
        }

        public void setMasterKeyBase64(String masterKeyBase64) {
            this.masterKeyBase64 = masterKeyBase64;
        }

        public String getPreviousMasterKeyVersion() {
            return previousMasterKeyVersion;
        }

        public void setPreviousMasterKeyVersion(String previousMasterKeyVersion) {
            this.previousMasterKeyVersion = previousMasterKeyVersion;
        }

        public String getPreviousMasterKeyBase64() {
            return previousMasterKeyBase64;
        }

        public void setPreviousMasterKeyBase64(String previousMasterKeyBase64) {
            this.previousMasterKeyBase64 = previousMasterKeyBase64;
        }
    }
}
