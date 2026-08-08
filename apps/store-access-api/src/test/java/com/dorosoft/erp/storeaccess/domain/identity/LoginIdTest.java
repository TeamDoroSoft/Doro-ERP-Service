package com.dorosoft.erp.storeaccess.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoginIdTest {

    @Test
    void normalizeTrimsAsciiWhitespaceAndLowercases() {
        LoginId loginId = LoginId.normalize("  Store.Owner-01  ");

        assertThat(loginId.value()).isEqualTo("store.owner-01");
    }

    @Test
    void rejectsLoginIdShorterThanFourCharacters() {
        assertThatThrownBy(() -> new LoginId("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLoginIdLongerThanFiftyCharacters() {
        String tooLong = "a".repeat(51);

        assertThatThrownBy(() -> new LoginId(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDisallowedCharacters() {
        assertThatThrownBy(() -> new LoginId("owner user"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLeadingOrTrailingNonAlphanumericCharacters() {
        assertThatThrownBy(() -> new LoginId("-owner01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginId("owner01."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnicodeCharacters() {
        assertThatThrownBy(() -> LoginId.normalize("店主0001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsMinimalValidLoginId() {
        LoginId loginId = new LoginId("own1");

        assertThat(loginId.value()).isEqualTo("own1");
    }
}
