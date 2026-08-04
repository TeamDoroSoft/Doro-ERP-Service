package com.dorosoft.erp.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import org.junit.jupiter.api.Test;

class IfMatchVersionTest {
    @Test
    void acceptsOnlyQuotedNonNegativeDecimalVersion() {
        assertThat(IfMatchVersion.parse("\"0\"")).isZero();
        assertThat(IfMatchVersion.parse("\"42\"")).isEqualTo(42L);
    }

    @Test
    void missingHeaderUsesPreconditionRequired() {
        assertThatThrownBy(() -> IfMatchVersion.parse(null))
                .isInstanceOfSatisfying(IdentityException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(IdentityErrorCode.PRECONDITION_REQUIRED));
    }

    @Test
    void malformedOrMismatchedVersionUsesPreconditionFailed() {
        assertThatThrownBy(() -> IfMatchVersion.parse("42"))
                .isInstanceOfSatisfying(IdentityException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(IdentityErrorCode.PRECONDITION_FAILED));
        assertThatThrownBy(() -> IfMatchVersion.verify(1, 2))
                .isInstanceOfSatisfying(IdentityException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(IdentityErrorCode.PRECONDITION_FAILED));
    }
}
