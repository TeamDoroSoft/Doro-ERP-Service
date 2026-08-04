package com.dorosoft.erp.identity.application.authentication;

record LoginTransactionOutcome(LoginResult success, Failure failure) {
    enum Failure {
        INVALID_CREDENTIALS,
        AUTHENTICATION_UNAVAILABLE
    }

    static LoginTransactionOutcome success(LoginResult result) {
        return new LoginTransactionOutcome(result, null);
    }

    static LoginTransactionOutcome failure(Failure failure) {
        return new LoginTransactionOutcome(null, failure);
    }
}
