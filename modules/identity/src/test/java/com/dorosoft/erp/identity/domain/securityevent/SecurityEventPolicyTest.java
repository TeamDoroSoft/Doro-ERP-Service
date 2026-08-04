package com.dorosoft.erp.identity.domain.securityevent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventPolicyTest {

    @Test
    void successAndFailureTableMatchesRegistry() {
        assertTrue(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_SUCCEEDED, SecurityEventOutcome.SUCCESS, null
        ));
        assertTrue(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE,
                SecurityEventFailureClass.CREDENTIAL_MISMATCH
        ));
        assertFalse(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE, null
        ));
    }

    @Test
    void rejectedEventRequiresFailureClass() {
        assertTrue(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_REJECTED, SecurityEventOutcome.DENIED,
                SecurityEventFailureClass.TEMPORARY_LOCK_ACTIVE
        ));
        assertFalse(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_REJECTED, SecurityEventOutcome.DENIED, null
        ));
    }

    @Test
    void rateLimitedEventRequiresNoFailureClass() {
        assertFalse(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_RATE_LIMITED, SecurityEventOutcome.DENIED,
                SecurityEventFailureClass.CREDENTIAL_MISMATCH
        ));
        assertTrue(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_RATE_LIMITED, SecurityEventOutcome.DENIED, null
        ));
    }

    @Test
    void accountNullableContractMatchesRegistry() {
        assertTrue(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE,
                SecurityEventFailureClass.CREDENTIAL_MISMATCH, false
        ));
        assertFalse(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_SUCCEEDED, SecurityEventOutcome.SUCCESS, null, false
        ));
        assertFalse(SecurityEventPolicy.isValidCombination(
                SecurityEventType.LOGIN_RATE_LIMITED, SecurityEventOutcome.DENIED, null, true
        ));
    }
}
