package com.dorosoft.erp.commerce.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Edge 또는 Store Access가 전달하는 Actor Context 검증 설정.
 *
 * <p>{@code secret}에는 안전한 기본값을 두지 않는다. 서명 검증이 켜져 있는데 Secret이 없으면
 * 애플리케이션이 기동하지 않고 Fail-Closed한다.
 */
@ConfigurationProperties(prefix = "doro.commerce.actor-context")
public class ActorContextProperties {

    /** 전달된 Actor Context의 서비스 자격증명 서명을 검증할지 여부. */
    private boolean signatureRequired = true;

    /** HMAC-SHA256 공유 Secret. 운영에서는 환경 변수로만 주입한다. */
    private String secret;

    /** 서명 재사용을 제한하는 허용 시간 오차. */
    private Duration maxAge = Duration.ofMinutes(5);

    public boolean isSignatureRequired() {
        return signatureRequired;
    }

    public void setSignatureRequired(boolean signatureRequired) {
        this.signatureRequired = signatureRequired;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }
}
