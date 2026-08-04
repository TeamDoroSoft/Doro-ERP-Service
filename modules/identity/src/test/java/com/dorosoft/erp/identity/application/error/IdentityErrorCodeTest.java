package com.dorosoft.erp.identity.application.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IdentityErrorCodeTest {

    @Test
    void exposesTheCompleteStableFeature01Catalogue() {
        assertThat(Arrays.stream(IdentityErrorCode.values()).map(IdentityErrorCode::code))
                .containsExactly(
                        "VALIDATION_FAILED", "CURRENT_PASSWORD_MISMATCH", "INVALID_ROLE",
                        "INVALID_PERMISSION", "INVALID_PERMISSION_COMBINATION", "IDEMPOTENCY_KEY_REQUIRED",
                        "INVALID_CURSOR", "INVALID_CREDENTIALS", "AUTHENTICATION_REQUIRED", "FORBIDDEN",
                        "CSRF_TOKEN_INVALID", "PASSWORD_CHANGE_REQUIRED", "ACCOUNT_NOT_FOUND",
                        "LOGIN_ID_DUPLICATE", "IDEMPOTENCY_KEY_REUSED", "REPRESENTATIVE_ROLE_IMMUTABLE",
                        "PRECONDITION_FAILED", "PRECONDITION_REQUIRED", "LOGIN_RATE_LIMITED",
                        "INTERNAL_SERVER_ERROR", "AUTHENTICATION_UNAVAILABLE", "IDEMPOTENCY_UNAVAILABLE",
                        "PRIVACY_ACCESS_LOG_UNAVAILABLE");
        assertThat(IdentityErrorCode.LOGIN_RATE_LIMITED.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE.status())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void exceptionNeverUsesItsCauseMessageAsPublicDetail() {
        var exception = new IdentityException(
                IdentityErrorCode.AUTHENTICATION_UNAVAILABLE,
                new IllegalStateException("redis-key-and-session-id"));

        assertThat(exception.detail()).isEqualTo(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE.defaultDetail());
        assertThat(exception.detail()).doesNotContain("redis", "session");
    }
}
