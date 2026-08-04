package com.dorosoft.erp.identity.infrastructure.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitLuaContractTest {

    @Test
    void oneScriptOwnsAtomicDualBucketDecisionAndEventMarker() throws IOException {
        String script;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "com/dorosoft/erp/identity/ratelimit/identity-login-rate-limit.lua")) {
            assertThat(stream).isNotNull();
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(script)
                .contains("math.min(activeLoginTokens, previousLoginTokens)")
                .contains("math.max(activeLoginLast, previousLoginLast)")
                .contains("if allowed then")
                .contains("loginTokens = loginTokens - scale")
                .contains("ipTokens = ipTokens - scale")
                .contains("'PENDING'")
                .contains("'RECORDED'")
                .contains("redis.call('PEXPIRE'")
                .doesNotContain("EVALSHA", "X-Forwarded-For");
    }
}
