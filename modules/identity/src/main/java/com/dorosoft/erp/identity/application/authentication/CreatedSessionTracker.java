package com.dorosoft.erp.identity.application.authentication;

final class CreatedSessionTracker {
    private String sessionId;

    void created(String value) {
        if (sessionId != null) {
            throw new IllegalStateException("A login transaction created more than one session");
        }
        sessionId = value;
    }

    String sessionId() {
        return sessionId;
    }
}
