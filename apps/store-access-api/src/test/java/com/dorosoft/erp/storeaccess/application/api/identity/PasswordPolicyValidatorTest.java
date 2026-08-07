package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityInfrastructureConfig;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityPasswordProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordPolicyValidatorTest {

    private final PasswordEncoder passwordEncoder =
            new IdentityInfrastructureConfig().passwordEncoder(new IdentityPasswordProperties(19456, 2, 1, 16, 32));
    private final PasswordPolicyValidator validator = new PasswordPolicyValidator(passwordEncoder);
    private final LoginId loginId = LoginId.normalize("owner01");

    @Test
    void acceptsAValidPassword() {
        validator.validate("a-perfectly-fine-passphrase-42", loginId, null);
    }

    @Test
    void rejectsPasswordShorterThanFifteenCharacters() {
        assertThatThrownBy(() -> validator.validate("short-pass", loginId, null))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsPasswordLongerThanOneHundredTwentyEightCharacters() {
        String tooLong = "a".repeat(129);

        assertThatThrownBy(() -> validator.validate(tooLong, loginId, null))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsBlocklistedPasswordsCaseInsensitively() {
        assertThatThrownBy(() -> validator.validate("CorrectHorseBatteryStaple", loginId, null))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsPasswordsContainingTheLoginId() {
        assertThatThrownBy(() -> validator.validate("prefix-owner01-suffix-value", loginId, null))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsPasswordsContainingServiceDerivedTerms() {
        assertThatThrownBy(() -> validator.validate("this-is-my-doro-erp-password", loginId, null))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsReusingTheCurrentPassword() {
        String currentPasswordHash = passwordEncoder.encode("an-existing-passphrase-99");

        assertThatThrownBy(() -> validator.validate("an-existing-passphrase-99", loginId, currentPasswordHash))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(PasswordManagementProblemCode.PASSWORD_REUSE_NOT_ALLOWED);
    }

    @Test
    void acceptsANewPasswordDifferentFromCurrent() {
        String currentPasswordHash = passwordEncoder.encode("an-existing-passphrase-99");

        validator.validate("a-completely-different-passphrase", loginId, currentPasswordHash);
    }

    @Test
    void nullCurrentPasswordHashSkipsTheReuseCheck() {
        validator.validate("brand-new-employee-passphrase", loginId, null);
    }

    @Test
    void normalizesUnicodeBeforeLengthCounting() {
        // "e" + combining acute accent (U+0065 U+0301), repeated 128 times: 256 raw code points, but NFC
        // composes each pair down to one precomposed character (U+00E9), landing exactly at the 128
        // max-length boundary. Proves normalization runs before the length check rather than after.
        String decomposedUnit = "é";
        String decomposed = decomposedUnit.repeat(128);
        assertThat(decomposed.codePointCount(0, decomposed.length())).isEqualTo(256);

        validator.validate(decomposed, loginId, null);
    }
}
