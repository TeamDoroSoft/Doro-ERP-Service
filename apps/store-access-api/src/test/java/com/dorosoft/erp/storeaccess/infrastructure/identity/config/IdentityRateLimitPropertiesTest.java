package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityRateLimitProperties.BucketSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityRateLimitPropertiesTest {

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new BucketSettings(0, 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRefillTokens() {
        assertThatThrownBy(() -> new BucketSettings(5, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRefillInterval() {
        assertThatThrownBy(() -> new BucketSettings(5, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAccountSettings() {
        assertThatThrownBy(() -> new IdentityRateLimitProperties(
                null, new BucketSettings(30, 6, Duration.ofMinutes(1))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingClientIpSettings() {
        assertThatThrownBy(() -> new IdentityRateLimitProperties(
                new BucketSettings(5, 1, Duration.ofMinutes(1)), null))
                .isInstanceOf(NullPointerException.class);
    }
}
