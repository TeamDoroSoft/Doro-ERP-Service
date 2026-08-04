package com.dorosoft.erp.identity.application.session;

public interface SessionIssuer {

    IssuedSession issue(AuthenticatedSession authenticatedSession);

    void delete(String sessionId);
}
