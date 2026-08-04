package com.dorosoft.erp.identity.domain.securityevent;

import java.util.Objects;

/** 데이터 모델의 10개 Event Type·Outcome·Failure Class·Account 조합 Registry. */
public final class SecurityEventPolicy {
    private SecurityEventPolicy() {
    }

    public static boolean isValidCombination(
            SecurityEventType type,
            SecurityEventOutcome outcome,
            SecurityEventFailureClass failureClass
    ) {
        return isValidCombination(
                type, outcome, failureClass, type != SecurityEventType.LOGIN_RATE_LIMITED
        );
    }

    public static boolean isValidCombination(
            SecurityEventType type,
            SecurityEventOutcome outcome,
            SecurityEventFailureClass failureClass,
            boolean accountIdentified
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(outcome, "outcome");
        return switch (type) {
            case LOGIN_SUCCEEDED -> outcome == SecurityEventOutcome.SUCCESS
                    && failureClass == null && accountIdentified;
            case LOGIN_FAILED -> outcome == SecurityEventOutcome.FAILURE
                    && failureClass == SecurityEventFailureClass.CREDENTIAL_MISMATCH;
            case LOGIN_REJECTED -> outcome == SecurityEventOutcome.DENIED
                    && accountIdentified
                    && (failureClass == SecurityEventFailureClass.ACCOUNT_INACTIVE
                    || failureClass == SecurityEventFailureClass.TEMPORARY_LOCK_ACTIVE
                    || failureClass == SecurityEventFailureClass.PERMANENT_LOCK_ACTIVE);
            case LOGIN_RATE_LIMITED -> outcome == SecurityEventOutcome.DENIED
                    && failureClass == null && !accountIdentified;
            case ACCOUNT_TEMPORARILY_LOCKED, ACCOUNT_PERMANENTLY_LOCKED,
                    LOGOUT_SUCCEEDED, PASSWORD_CHANGED, PASSWORD_RESET ->
                    outcome == SecurityEventOutcome.SUCCESS && failureClass == null && accountIdentified;
            case LOGIN_SESSION_CREATE_FAILED -> outcome == SecurityEventOutcome.FAILURE
                    && failureClass == SecurityEventFailureClass.SESSION_STORE_UNAVAILABLE
                    && accountIdentified;
        };
    }
}
